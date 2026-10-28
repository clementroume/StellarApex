package apex.stellar.antares.service;

import apex.stellar.antares.dto.AuthenticationRequest;
import apex.stellar.antares.dto.RegisterRequest;
import apex.stellar.antares.dto.TokenRefreshResponse;
import apex.stellar.antares.dto.UserResponse;
import apex.stellar.antares.exception.AccountLockedException;
import apex.stellar.antares.exception.DataConflictException;
import apex.stellar.antares.exception.ResourceNotFoundException;
import apex.stellar.antares.exception.TooManyAttemptsException;
import apex.stellar.antares.mapper.UserMapper;
import apex.stellar.antares.model.PlatformRole;
import apex.stellar.antares.model.User;
import apex.stellar.antares.repository.jpa.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for handling user authentication, registration, token issuance, and logout. */
@Service
@RequiredArgsConstructor
public class AuthenticationService {

  static final String REGISTRATION_KEY_PREFIX = "registrations_ip:";

  private final UserRepository repository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final AuthenticationManager authenticationManager;
  private final UserMapper userMapper;
  private final RefreshTokenService refreshTokenService;
  private final CookieService cookieService;
  private final LoginAttemptService loginAttemptService;
  private final SupportSessionService supportSessionService;
  private final EmailVerificationService emailVerificationService;
  private final SlidingWindowLimiter limiter;
  private final Clock clock;

  @Value("${application.security.registration.max-per-ip:10}")
  private int maxRegistrationsPerIp;

  @Value("${application.security.registration.window:PT1H}")
  private Duration registrationWindow;

  /**
   * Registers a new user, issues JWT tokens and mails an e-mail verification link.
   *
   * <p>Registrations are throttled per client IP (every attempt counts, conflicts included), so one
   * client cannot multiply accounts to get around per-user limits.
   *
   * @param request The registration request containing user details.
   * @param clientIp The client IP (see {@link ClientIpResolver}).
   * @param response The HTTP response to set cookies.
   * @return UserResponse containing the registered user's details.
   * @throws DataConflictException if the email is already in use.
   * @throws TooManyAttemptsException if this IP registered too often recently.
   */
  @Transactional
  public UserResponse register(
      RegisterRequest request, String clientIp, HttpServletResponse response) {

    long now = clock.millis();
    String throttleKey = REGISTRATION_KEY_PREFIX + clientIp;
    long retryAfter =
        limiter.retryAfterSeconds(throttleKey, maxRegistrationsPerIp, registrationWindow, now);
    if (retryAfter > 0) {
      throw new TooManyAttemptsException(
          "error.registration.too.many", retryAfter, SlidingWindowLimiter.minutes(retryAfter));
    }
    limiter.record(throttleKey, registrationWindow, now);

    if (repository.findByEmail(request.email()).isPresent()) {
      throw new DataConflictException("error.email.in.use", request.email());
    }

    User savedUser =
        repository.save(
            User.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .platformRole(PlatformRole.USER)
                .locale(request.locale() != null ? request.locale() : "en")
                .emailVerified(false)
                .build());

    emailVerificationService.sendVerification(savedUser);
    issueTokensAndSetCookies(savedUser, response);

    return userMapper.toUserResponse(savedUser);
  }

  /**
   * Authenticates a user and issues JWT tokens.
   *
   * @param request The authentication request containing user credentials.
   * @param response The HTTP response to set cookies.
   * @return UserResponse containing the authenticated user's details.
   * @throws ResourceNotFoundException if the user email does not exist.
   */
  @Transactional
  public UserResponse login(AuthenticationRequest request, HttpServletResponse response) {

    // Vérifier si le compte est verrouillé
    if (loginAttemptService.isBlocked(request.email())) {
      long secondsRemaining = loginAttemptService.getBlockTimeRemaining(request.email());
      throw new AccountLockedException("error.account.locked", secondsRemaining / 60);
    }

    try {
      authenticationManager.authenticate(
          new UsernamePasswordAuthenticationToken(request.email(), request.password()));

      User user =
          repository
              .findByEmail(request.email())
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException("error.user.not.found.email", request.email()));

      // Réinitialiser les tentatives en cas de succès
      loginAttemptService.loginSucceeded(request.email());

      issueTokensAndSetCookies(user, response);
      return userMapper.toUserResponse(user);

    } catch (BadCredentialsException e) {
      // Enregistrer l'échec
      loginAttemptService.loginFailed(request.email());
      throw e;
    }
  }

  /**
   * Refreshes JWT tokens using the provided refresh token.
   *
   * @param oldRefreshToken The (raw) old refresh token from the user's cookie.
   * @param response The HTTP response to set new cookies.
   * @return TokenRefreshResponse containing the new access token.
   * @throws ResourceNotFoundException if the refresh token is not found in Redis.
   */
  @Transactional
  public TokenRefreshResponse refreshToken(String oldRefreshToken, HttpServletResponse response) {

    String newAccessToken =
        issueTokensAndSetCookies(
            refreshTokenService
                .findUserByToken(oldRefreshToken)
                .orElseThrow(() -> new ResourceNotFoundException("error.token.refresh.notfound")),
            response);

    return new TokenRefreshResponse(newAccessToken);
  }

  /**
   * Logs out the user by deleting their refresh token from Redis, ending any active support session
   * they started, and clearing cookies.
   *
   * @param currentUser The currently authenticated user.
   * @param response The HTTP response to clear cookies.
   */
  public void logout(User currentUser, HttpServletResponse response) {

    refreshTokenService.deleteTokenForUser(currentUser);
    supportSessionService.endActiveSessions(currentUser, response);
    cookieService.clearCookie(jwtService.getAccessTokenCookieName(), response);
    cookieService.clearCookie(jwtService.getRefreshTokenCookieName(), response);
  }

  /**
   * Issues fresh cookies for the current session after a password change: the new access token is
   * accepted (not older than the change), and the new refresh token replaces the user's previous
   * one, so other devices are signed out.
   *
   * @param user The user who changed their password.
   * @param response The HTTP response to set cookies.
   */
  public void reissueSession(User user, HttpServletResponse response) {
    issueTokensAndSetCookies(user, response);
  }

  /**
   * Centralized method to issue new tokens and set them in cookies.
   *
   * @param user The user for whom to issue tokens.
   * @param response The HTTP response.
   * @return The newly generated access token.
   */
  private String issueTokensAndSetCookies(User user, HttpServletResponse response) {

    String accessToken = jwtService.generateToken(user);

    cookieService.addCookie(
        jwtService.getAccessTokenCookieName(),
        accessToken,
        jwtService.getAccessTokenDurationMs(),
        response);

    cookieService.addCookie(
        jwtService.getRefreshTokenCookieName(),
        refreshTokenService.createRefreshToken(user),
        jwtService.getRefreshTokenDurationMs(),
        response);

    return accessToken;
  }
}
