package apex.stellar.antares.service;

import apex.stellar.antares.exception.BusinessRuleException;
import apex.stellar.antares.exception.EmailNotVerifiedException;
import apex.stellar.antares.exception.TooManyAttemptsException;
import apex.stellar.antares.model.User;
import apex.stellar.antares.repository.jpa.UserRepository;
import apex.stellar.antares.service.AccountTokenService.Purpose;
import java.time.Clock;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * E-mail verification: a link mailed at registration (and after an email change), resendable by
 * the user (throttled per user), which sets {@code User.emailVerified}.
 *
 * <p>An unverified account can sign in, log personal scores and use its profile, but cannot join a
 * gym nor request a new one ({@link #requireVerified}): multiplying throwaway accounts must not
 * multiply enrollment-code guesses or gym requests. Accounts created before e-mail verification
 * existed are verified (migration V8).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

  static final String RESEND_KEY_PREFIX = "email_verification_resend:";

  private final UserRepository userRepository;
  private final AccountTokenService accountTokenService;
  private final SlidingWindowLimiter limiter;
  private final ApplicationEventPublisher events;
  private final CacheManager cacheManager;
  private final Clock clock;

  @Value("${application.security.email-verification.max-resends:3}")
  private int maxResends;

  @Value("${application.security.email-verification.window:PT1H}")
  private Duration window;

  /**
   * Refuses an action reserved to verified addresses.
   *
   * @param user The acting user.
   * @throws EmailNotVerifiedException When the address is not verified (403).
   */
  public void requireVerified(User user) {
    if (!user.isEmailVerified()) {
      throw new EmailNotVerifiedException();
    }
  }

  /**
   * Mails a verification link to the user's current address (registration, email change).
   *
   * @param user The unverified user.
   */
  public void sendVerification(User user) {
    events.publishEvent(
        new AccountMail(
            AccountMail.Kind.EMAIL_VERIFICATION,
            user.getEmail(),
            user.getLocale(),
            user.getFirstName(),
            accountTokenService.issue(user, Purpose.EMAIL_VERIFICATION),
            accountTokenService.validity(Purpose.EMAIL_VERIFICATION)));
  }

  /**
   * Mails a new verification link on the user's request; does nothing when already verified.
   *
   * @param currentUser The authenticated user.
   * @throws TooManyAttemptsException When the user asked too often.
   */
  @Transactional(readOnly = true)
  public void resend(User currentUser) {
    User user = userRepository.findById(currentUser.getId()).orElse(currentUser);
    if (user.isEmailVerified()) {
      return;
    }
    long now = clock.millis();
    String key = RESEND_KEY_PREFIX + user.getId();
    long retryAfter = limiter.retryAfterSeconds(key, maxResends, window, now);
    if (retryAfter > 0) {
      throw new TooManyAttemptsException(
          "error.email.verification.too.many",
          retryAfter,
          SlidingWindowLimiter.minutes(retryAfter));
    }
    limiter.record(key, window, now);
    sendVerification(user);
  }

  /**
   * Confirms an address with a verification token.
   *
   * @param rawToken The token from the link.
   * @throws BusinessRuleException When the token is unknown, expired, used or was sent to another
   *     address than the account's current one (400 {@code error.token.invalid}).
   */
  @Transactional
  public void verify(String rawToken) {
    User user =
        accountTokenService
            .consume(Purpose.EMAIL_VERIFICATION, rawToken)
            .flatMap(
                owner ->
                    userRepository
                        .findById(owner.userId())
                        .filter(u -> u.getEmail().equals(owner.email())))
            .orElseThrow(() -> new BusinessRuleException("error.token.invalid"));
    user.setEmailVerified(true);
    userRepository.save(user);
    Cache users = cacheManager.getCache("users");
    if (users != null) {
      users.evict(user.getEmail());
    }
    log.info("E-mail verified for user {}", user.getId());
  }
}
