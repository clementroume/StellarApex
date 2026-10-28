package apex.stellar.antares.service;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Component;

/**
 * Redis sliding-window counter shared by the throttles (wrong enrollment codes, registrations,
 * password-reset and verification e-mails).
 *
 * <p>Each event is stored in a sorted set scored by its time; events older than the window drop
 * out. Callers pass the current time so they can be tested with a fixed clock.
 */
@Component
@RequiredArgsConstructor
public class SlidingWindowLimiter {

  private final StringRedisTemplate redisTemplate;

  /**
   * Records one event under the key.
   *
   * @param key The Redis key (prefix + subject).
   * @param window The window length; the key expires with it.
   * @param nowMillis The current time.
   */
  public void record(String key, Duration window, long nowMillis) {
    redisTemplate.opsForZSet().add(key, nowMillis + ":" + UUID.randomUUID(), nowMillis);
    redisTemplate.opsForZSet().removeRangeByScore(key, 0, nowMillis - window.toMillis());
    redisTemplate.expire(key, window);
  }

  /**
   * Seconds before a new event is allowed: 0 below the maximum, else the time until the event that
   * keeps the count at the maximum leaves the window (at least 1).
   *
   * @param key The Redis key.
   * @param max The maximum number of events in the window.
   * @param window The window length.
   * @param nowMillis The current time.
   * @return The seconds to wait, 0 if an event is allowed now.
   */
  public long retryAfterSeconds(String key, int max, Duration window, long nowMillis) {
    long windowMs = window.toMillis();
    redisTemplate.opsForZSet().removeRangeByScore(key, 0, nowMillis - windowMs);
    Long count = redisTemplate.opsForZSet().zCard(key);
    if (count == null || count < max) {
      return 0;
    }
    long index = count - max;
    Set<TypedTuple<String>> blocking =
        redisTemplate.opsForZSet().rangeWithScores(key, index, index);
    if (blocking == null || blocking.isEmpty()) {
      return 0;
    }
    Double score = blocking.iterator().next().getScore();
    long expiresAt = (score == null ? nowMillis : score.longValue()) + windowMs;
    return Math.max(1, (expiresAt - nowMillis + 999) / 1000);
  }

  /**
   * Converts seconds to wait into whole minutes for messages (at least 1).
   *
   * @param seconds The seconds to wait.
   * @return The minutes, rounded up.
   */
  public static long minutes(long seconds) {
    return Math.max(1, (seconds + 59) / 60);
  }
}
