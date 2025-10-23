package com.antares.api.service;

import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

/**
 * RateLimitingService provides functionality to enforce rate limiting using Redis as the backing
 * store.
 */
@Service
public class RateLimitingService {

  private final StringRedisTemplate redisTemplate;

  /**
   * Constructs a RateLimitingService with the specified StringRedisTemplate.
   *
   * @param redisTemplate the StringRedisTemplate for Redis operations
   */
  public RateLimitingService(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  /**
   * Checks if a request is allowed based on the rate limit defined by the limit and duration.
   *
   * @param key the unique key to identify the rate limit (e.g., user ID or IP address)
   * @param limit the maximum number of requests allowed within the duration
   * @param duration the time duration for the rate limit
   * @param unit the time unit for the duration (e.g., SECONDS, MINUTES)
   * @return true if the request is allowed, false if the rate limit has been exceeded
   */
  public boolean isAllowed(String key, int limit, long duration, TimeUnit unit) {

    ValueOperations<String, String> ops = redisTemplate.opsForValue();

    String currentCountStr = ops.get(key);
    if (currentCountStr == null) {
      ops.set(key, "1", duration, unit);
      return true;
    }

    long currentCount = Long.parseLong(currentCountStr);
    if (currentCount < limit) {
      ops.increment(key);
      return true;
    }

    return false;
  }
}
