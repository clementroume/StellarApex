package apex.stellar.antares.service;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Service responsible for managing failed login attempts and enforcing temporary account locking to
 * prevent brute-force attacks.
 *
 * <p>It leverages Redis to store attempt counts and lock status with automatic expiration (TTL),
 * ensuring a stateless and scalable implementation.
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

  private static final String ATTEMPT_PREFIX = "login_attempts:";
  private static final String LOCK_PREFIX = "account_locked:";

  private final StringRedisTemplate redisTemplate;

  // Injectable configuration with safe defaults (5 attempts, 15 minutes lock)
  @Value("${application.security.login.max-attempts:5}")
  private int maxAttempts;

  @Value("${application.security.login.lock-duration:900000}")
  private long lockDurationMs;

  /**
   * Records a failed login attempt for the specified email.
   *
   * <p>This method increments the failure count in Redis. If the count reaches the configured
   * maximum, the account is locked for the defined duration, and the attempt counter is cleared.
   *
   * @param email The email of the user who failed to log in.
   */
  public void loginFailed(String email) {
    String attemptKey = ATTEMPT_PREFIX + email;
    String lockKey = LOCK_PREFIX + email;

    // Atomically increment the attempt count
    Long attempts = redisTemplate.opsForValue().increment(attemptKey);

    // On the first failure, set the expiration for the attempt counter window
    // ensuring we don't keep stale counters forever.
    if (attempts != null && attempts == 1) {
      redisTemplate.expire(attemptKey, lockDurationMs, TimeUnit.MILLISECONDS);
    }

    // Check if the security threshold is reached
    if (attempts != null && attempts >= maxAttempts) {
      // Lock the account by setting a specific flag key
      redisTemplate.opsForValue().set(lockKey, "true", lockDurationMs, TimeUnit.MILLISECONDS);

      // Clean up the attempt counter as the penalty is now applied
      redisTemplate.delete(attemptKey);
    }
  }

  /**
   * Resets the login attempts and lock status upon a successful login.
   *
   * <p>This ensures that a legitimate user who made a typo isn't penalized later.
   *
   * @param email The email of the authenticated user.
   */
  public void loginSucceeded(String email) {
    redisTemplate.delete(ATTEMPT_PREFIX + email);
    redisTemplate.delete(LOCK_PREFIX + email);
  }

  /**
   * Checks if the account associated with the email is currently locked.
   *
   * @param email The email to check.
   * @return {@code true} if the account is locked, {@code false} otherwise.
   */
  public boolean isBlocked(String email) {
    return Boolean.TRUE.equals(redisTemplate.hasKey(LOCK_PREFIX + email));
  }

  /**
   * Retrieves the remaining time in seconds before the account is unlocked.
   *
   * @param email The locked email.
   * @return The remaining lock duration in seconds, or 0 if the account is not locked.
   */
  public long getBlockTimeRemaining(String email) {
    Long ttl = redisTemplate.getExpire(LOCK_PREFIX + email, TimeUnit.SECONDS);

    // Redis getExpire returns -2 if the key does not exist, or -1 if it has no expiry.
    // We normalize these values to 0 for the API response.
    return (ttl != null && ttl > 0) ? ttl : 0;
  }
}
