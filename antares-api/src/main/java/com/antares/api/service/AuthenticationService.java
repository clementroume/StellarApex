package com.antares.api.service;

import com.antares.api.dto.AuthenticationRequest;
import com.antares.api.dto.RegisterRequest;
import com.antares.api.dto.TokenRefreshResponse;
import com.antares.api.dto.UserResponse;
import com.antares.api.exception.DataConflictException;
import com.antares.api.exception.ResourceNotFoundException;
import com.antares.api.mapper.UserMapper;
import com.antares.api.model.Role;
import com.antares.api.model.User;
import com.antares.api.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for handling user authentication, registration, token issuance, and logout. */
@Service
@RequiredArgsConstructor
public class AuthenticationService {

  private final UserRepository repository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final AuthenticationManager authenticationManager;
  private final UserMapper userMapper;
  private final RefreshTokenService refreshTokenService;
  private final CookieService cookieService;

  /**
   * Registers a new user and issues JWT tokens.
   *
   * @param request The registration request containing user details.
   * @param response The HTTP response to set cookies.
   * @return UserResponse containing the registered user's details.
   * @throws DataConflictException if the email is already in use.
   */
  @Transactional
  public UserResponse register(RegisterRequest request, HttpServletResponse response) {

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
                .role(Role.ROLE_USER)
                .build());

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

    authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(request.email(), request.password()));

    User user =
        repository
            .findByEmail(request.email())
            .orElseThrow(
                () -> new ResourceNotFoundException("error.user.not.found.email", request.email()));

    issueTokensAndSetCookies(user, response);

    return userMapper.toUserResponse(user);
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
   * Logs out the user by deleting their refresh token from Redis and clearing cookies.
   *
   * @param currentUser The currently authenticated user.
   * @param response The HTTP response to clear cookies.
   */
  public void logout(User currentUser, HttpServletResponse response) {

    refreshTokenService.deleteTokenForUser(currentUser);
    cookieService.clearCookie(jwtService.getAccessTokenCookieName(), response);
    cookieService.clearCookie(jwtService.getRefreshTokenCookieName(), response);
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
