package apex.stellar.antares.service;

import apex.stellar.antares.exception.TooManyAttemptsException;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Service;

/**
 * Throttles wrong enrollment codes per user, so codes chosen by gyms (e.g. "SPARTA") cannot be
 * guessed by trying many values.
 *
 * <p>Each wrong code is stored in a Redis sorted set scored by its time; failures older than the
 * window drop out (sliding window). Once a user reaches the maximum within the window, every join
 * attempt, for any gym and even with a valid code, is refused (429) until enough failures expire.
 * A successful join does not reset the count: knowing one code (e.g. of a gym the attacker created)
 * must not unlock more guesses on another gym.
 */
@Service
@RequiredArgsConstructor
public class EnrollmentAttemptService {

  static final String KEY_PREFIX = "enrollment_failures:";
  private static final String ERROR_TOO_MANY = "error.enrollment.too.many";

  private final StringRedisTemplate redisTemplate;

  /** Time source, replaceable in tests. */
  Clock clock = Clock.systemUTC();

  @Value("${application.security.enrollment.max-failures:5}")
  private int maxFailures;

  @Value("${application.security.enrollment.window:PT15M}")
  private Duration window;

  /**
   * Refuses the attempt when the user already reached the maximum of wrong codes in the window.
   *
   * @param userId The user trying to join.
   * @throws TooManyAttemptsException With the seconds to wait (and minutes in the message).
   */
  public void checkAllowed(Long userId) {
    long retryAfter = retryAfterSeconds(userId);
    if (retryAfter > 0) {
      throw new TooManyAttemptsException(
          ERROR_TOO_MANY, retryAfter, Math.max(1, (retryAfter + 59) / 60));
    }
  }

  /**
   * Records a wrong enrollment code for the user.
   *
   * @param userId The user who entered the wrong code.
   */
  public void recordFailure(Long userId) {
    String key = key(userId);
    long now = clock.millis();
    redisTemplate.opsForZSet().add(key, now + ":" + UUID.randomUUID(), now);
    redisTemplate.opsForZSet().removeRangeByScore(key, 0, now - window.toMillis());
    redisTemplate.expire(key, window);
  }

  /**
   * Seconds before the user may try again: 0 below the maximum, else the time until the failure
   * that keeps the count at the maximum leaves the window (at least 1).
   *
   * @param userId The user.
   * @return The seconds to wait, 0 if an attempt is allowed now.
   */
  long retryAfterSeconds(Long userId) {
    String key = key(userId);
    long now = clock.millis();
    long windowMs = window.toMillis();
    redisTemplate.opsForZSet().removeRangeByScore(key, 0, now - windowMs);
    Long count = redisTemplate.opsForZSet().zCard(key);
    if (count == null || count < maxFailures) {
      return 0;
    }
    long index = count - maxFailures;
    Set<TypedTuple<String>> blocking =
        redisTemplate.opsForZSet().rangeWithScores(key, index, index);
    if (blocking == null || blocking.isEmpty()) {
      return 0;
    }
    Double score = blocking.iterator().next().getScore();
    long expiresAt = (score == null ? now : score.longValue()) + windowMs;
    return Math.max(1, (expiresAt - now + 999) / 1000);
  }

  private static String key(Long userId) {
    return KEY_PREFIX + userId;
  }
}
