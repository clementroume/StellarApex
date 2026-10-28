package apex.stellar.antares.service;

import apex.stellar.antares.dto.ResetPasswordRequest;
import apex.stellar.antares.exception.BusinessRuleException;
import apex.stellar.antares.exception.InvalidPasswordException;
import apex.stellar.antares.exception.TooManyAttemptsException;
import apex.stellar.antares.model.User;
import apex.stellar.antares.repository.jpa.UserRepository;
import apex.stellar.antares.service.AccountTokenService.Purpose;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Forgotten password: a reset link by e-mail, then a new password with the link's token.
 *
 * <p>The request never tells whether an account exists (same answer, mail sent asynchronously).
 * Requests are throttled per client IP (429) and, silently, per address (no mail bombing). A reset
 * sets {@code passwordChangedAt} (older access tokens are refused by the JWT filter), deletes the
 * refresh token, unlocks the login and marks the address verified (the link proved the mailbox).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

  static final String IP_KEY_PREFIX = "password_reset_ip:";
  static final String EMAIL_KEY_PREFIX = "password_reset_email:";

  private final UserRepository userRepository;
  private final AccountTokenService accountTokenService;
  private final SlidingWindowLimiter limiter;
  private final ApplicationEventPublisher events;
  private final PasswordEncoder passwordEncoder;
  private final RefreshTokenService refreshTokenService;
  private final LoginAttemptService loginAttemptService;
  private final CacheManager cacheManager;
  private final Clock clock;

  @Value("${application.security.password-reset.max-per-ip:5}")
  private int maxPerIp;

  @Value("${application.security.password-reset.max-per-email:3}")
  private int maxPerEmail;

  @Value("${application.security.password-reset.window:PT1H}")
  private Duration window;

  /**
   * Mails a reset link when the address belongs to an enabled account; does nothing otherwise.
   *
   * @param email The address typed by the user.
   * @param clientIp The client IP, for throttling.
   * @throws TooManyAttemptsException When this IP asked too often.
   */
  @Transactional(readOnly = true)
  public void requestReset(String email, String clientIp) {
    long now = clock.millis();
    String ipKey = IP_KEY_PREFIX + clientIp;
    long retryAfter = limiter.retryAfterSeconds(ipKey, maxPerIp, window, now);
    if (retryAfter > 0) {
      throw new TooManyAttemptsException(
          "error.password.reset.too.many", retryAfter, SlidingWindowLimiter.minutes(retryAfter));
    }
    limiter.record(ipKey, window, now);

    String emailKey = EMAIL_KEY_PREFIX + email.trim().toLowerCase(Locale.ROOT);
    if (limiter.retryAfterSeconds(emailKey, maxPerEmail, window, now) > 0) {
      log.warn("Password reset: too many mails for one address, request ignored");
      return;
    }
    limiter.record(emailKey, window, now);

    userRepository
        .findByEmail(email.trim())
        .filter(User::isEnabled)
        .ifPresent(
            user ->
                events.publishEvent(
                    new AccountMail(
                        AccountMail.Kind.PASSWORD_RESET,
                        user.getEmail(),
                        user.getLocale(),
                        user.getFirstName(),
                        accountTokenService.issue(user, Purpose.PASSWORD_RESET),
                        accountTokenService.validity(Purpose.PASSWORD_RESET))));
  }

  /**
   * Sets a new password with a reset token, then ends every session of the user.
   *
   * @param request The token and the new password.
   * @throws InvalidPasswordException When the confirmation differs (the token is kept).
   * @throws BusinessRuleException When the token is unknown, expired, used or no longer matches
   *     the account address (400 {@code error.token.invalid}).
   */
  @Transactional
  public void resetPassword(ResetPasswordRequest request) {
    if (!request.newPassword().equals(request.confirmationPassword())) {
      throw new InvalidPasswordException("error.password.mismatch");
    }
    User user =
        accountTokenService
            .consume(Purpose.PASSWORD_RESET, request.token())
            .flatMap(
                owner ->
                    userRepository
                        .findById(owner.userId())
                        .filter(u -> u.getEmail().equals(owner.email())))
            .orElseThrow(() -> new BusinessRuleException("error.token.invalid"));

    user.setPassword(passwordEncoder.encode(request.newPassword()));
    user.setPasswordChangedAt(clock.instant());
    user.setEmailVerified(true);
    userRepository.save(user);

    refreshTokenService.deleteTokenForUser(user);
    loginAttemptService.loginSucceeded(user.getEmail());
    Cache users = cacheManager.getCache("users");
    if (users != null) {
      users.evict(user.getEmail());
    }
    log.info("Password reset for user {}", user.getId());
  }
}
