package com.antares.api.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Unit tests for {@link RateLimitingService}.
 *
 * <p>Each test follows the Given/When/Then pattern for clarity and maintainability.
 *
 * <p>Given: The initial state or preconditions for the test. When: The action or event being
 * tested. Then: The expected outcome or assertion.
 */
@ExtendWith(MockitoExtension.class)
class RateLimitingServiceTest {

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  @InjectMocks private RateLimitingService rateLimitingService;

  @BeforeEach
  void setUp() {
    // Given: Mocked RedisTemplate returns mocked ValueOperations.
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
  }

  @Test
  @DisplayName("isAllowed should allow the first request")
  void testIsAllowed_firstRequest_shouldAllow() {
    // Given: No value exists for the key in Redis.
    String key = "test-key";
    when(valueOperations.get(key)).thenReturn(null);

    // When: Checking if the first request is allowed.
    boolean isAllowed = rateLimitingService.isAllowed(key, 5, 1, TimeUnit.MINUTES);

    // Then: The request is allowed and the key is set with count 1 and correct TTL.
    assertTrue(isAllowed);
    verify(valueOperations).set(key, "1", 1, TimeUnit.MINUTES);
  }

  @Test
  @DisplayName("isAllowed should allow subsequent requests within the limit")
  void testIsAllowed_withinLimit_shouldAllowAndIncrement() {
    // Given: The key exists in Redis with a count below the limit.
    String key = "test-key";
    when(valueOperations.get(key)).thenReturn("3");

    // When: Checking if the request is allowed.
    boolean isAllowed = rateLimitingService.isAllowed(key, 5, 1, TimeUnit.MINUTES);

    // Then: The request is allowed and the count is incremented.
    assertTrue(isAllowed);
    verify(valueOperations).increment(key);
  }

  @Test
  @DisplayName("isAllowed should deny requests exceeding the limit")
  void testIsAllowed_exceedingLimit_shouldDeny() {
    // Given: The key exists in Redis with a count equal to the limit.
    String key = "test-key";
    when(valueOperations.get(key)).thenReturn("5"); // Limit is 5

    // When: Checking if the request is allowed.
    boolean isAllowed = rateLimitingService.isAllowed(key, 5, 1, TimeUnit.MINUTES);

    // Then: The request is denied and no increment or set is performed.
    assertFalse(isAllowed);
    verify(valueOperations, never()).increment(key);
    verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
  }
}
