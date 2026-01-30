package apex.stellar.antares.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

  // Constants for testing
  private final String email = "hacker@example.com";
  private final String attemptKey = "login_attempts:" + email;
  private final String lockKey = "account_locked:" + email;
  private final int maxAttempts = 3;
  private final long lockDuration = 60000L; // 1 minute in ms
  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;
  @InjectMocks private LoginAttemptService loginAttemptService;

  @BeforeEach
  void setUp() {
    // Mock the redisTemplate.opsForValue() call chain
    // 'lenient()' is used because some tests do not call this method (e.g., loginSucceeded)
    lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    // Manually inject @Value properties (normally done by Spring)
    ReflectionTestUtils.setField(loginAttemptService, "maxAttempts", maxAttempts);
    ReflectionTestUtils.setField(loginAttemptService, "lockDurationMs", lockDuration);
  }

  @Test
  @DisplayName("loginFailed: First attempt should increment counter and set expiration")
  void loginFailed_firstAttempt() {
    // Given
    when(valueOperations.increment(attemptKey)).thenReturn(1L);

    // When
    loginAttemptService.loginFailed(email);

    // Then
    verify(valueOperations).increment(attemptKey);
    // Verify that expiration is set on the first failure
    verify(redisTemplate).expire(attemptKey, lockDuration, TimeUnit.MILLISECONDS);
    // Verify that we do NOT lock the account immediately
    verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any());
  }

  @Test
  @DisplayName("loginFailed: Second attempt should only increment")
  void loginFailed_secondAttempt() {
    // Given
    when(valueOperations.increment(attemptKey)).thenReturn(2L);

    // When
    loginAttemptService.loginFailed(email);

    // Then
    verify(valueOperations).increment(attemptKey);
    // No new expiration, no lock
    verify(redisTemplate, never()).expire(anyString(), anyLong(), any());
    verify(valueOperations, never()).set(eq(lockKey), anyString(), anyLong(), any());
  }

  @Test
  @DisplayName("loginFailed: Max attempts reached should lock account and clear attempts")
  void loginFailed_maxAttempts() {
    // Given
    when(valueOperations.increment(attemptKey)).thenReturn((long) maxAttempts);

    // When
    loginAttemptService.loginFailed(email);

    // Then
    // 1. Must set the lock (key 'account_locked')
    verify(valueOperations).set(lockKey, "true", lockDuration, TimeUnit.MILLISECONDS);
    // 2. Must delete the attempt counter to start fresh after the ban
    verify(redisTemplate).delete(attemptKey);
  }

  @Test
  @DisplayName("loginSucceeded: Should clear both attempts and lock keys")
  void loginSucceeded() {
    // When
    loginAttemptService.loginSucceeded(email);

    // Then
    verify(redisTemplate).delete(attemptKey);
    verify(redisTemplate).delete(lockKey);
  }

  @Test
  @DisplayName("isBlocked: Should return true if lock key exists")
  void isBlocked_true() {
    // Given
    when(redisTemplate.hasKey(lockKey)).thenReturn(true);

    // When
    boolean result = loginAttemptService.isBlocked(email);

    // Then
    assertTrue(result);
  }

  @Test
  @DisplayName("isBlocked: Should return false if lock key is missing")
  void isBlocked_false() {
    // Given
    when(redisTemplate.hasKey(lockKey)).thenReturn(false);

    // When
    boolean result = loginAttemptService.isBlocked(email);

    // Then
    assertFalse(result);
  }

  @Test
  @DisplayName("getBlockTimeRemaining: Should return TTL if key exists")
  void getBlockTimeRemaining_exists() {
    // Given
    long expectedTtl = 30L;
    when(redisTemplate.getExpire(lockKey, TimeUnit.SECONDS)).thenReturn(expectedTtl);

    // When
    long result = loginAttemptService.getBlockTimeRemaining(email);

    // Then
    assertEquals(expectedTtl, result);
  }

  @Test
  @DisplayName("getBlockTimeRemaining: Should return 0 if key doesn't exist (TTL -2)")
  void getBlockTimeRemaining_notExists() {
    // Given
    when(redisTemplate.getExpire(lockKey, TimeUnit.SECONDS)).thenReturn(-2L);

    // When
    long result = loginAttemptService.getBlockTimeRemaining(email);

    // Then
    assertEquals(0, result);
  }

  @Test
  @DisplayName("getBlockTimeRemaining: Should return 0 if Redis returns null")
  void getBlockTimeRemaining_null() {
    // Given
    when(redisTemplate.getExpire(lockKey, TimeUnit.SECONDS)).thenReturn(null);

    // When
    long result = loginAttemptService.getBlockTimeRemaining(email);

    // Then
    assertEquals(0, result);
  }
}