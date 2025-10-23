package com.antares.api.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.antares.api.config.JwtProperties;
import com.antares.api.model.User;
import com.antares.api.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Unit tests for the {@link RefreshTokenService} with hashing logic.
 *
 * <p>Each test follows the Given/When/Then pattern for clarity and maintainability.
 *
 * <p>Given: The initial state or preconditions for the test. When: The action or event being
 * tested. Then: The expected outcome or assertion.
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

  @Mock private RedisTemplate<String, String> redisTemplate;
  @Mock private UserRepository userRepository;
  @Mock private JwtProperties jwtProperties;
  @Mock private ValueOperations<String, String> valueOperations;

  @InjectMocks private RefreshTokenService refreshTokenService;

  // Helper to replicate the hashing logic in tests
  private String hashValue(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  @BeforeEach
  void setUp() {
    // Given: RedisTemplate returns mocked ValueOperations.
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
  }

  @Test
  @DisplayName("createRefreshToken should save hashed tokens in Redis and return raw token")
  void createRefreshToken_shouldGenerateAndSaveTokenInRedis() {
    // Given: A user and refresh token properties.
    User user = new User();
    user.setId(1L);

    JwtProperties.RefreshToken refreshTokenProps = mock(JwtProperties.RefreshToken.class);
    when(jwtProperties.refreshToken()).thenReturn(refreshTokenProps);
    when(refreshTokenProps.expiration()).thenReturn(604800000L);

    // When: Creating a refresh token.
    String rawToken = refreshTokenService.createRefreshToken(user);

    // Then: The raw token is not null and hashed values are stored in Redis.
    assertNotNull(rawToken);

    String hashedToken = hashValue(rawToken);
    String hashedUserId = hashValue(user.getId().toString());

    verify(valueOperations)
        .set(eq("refresh_token::" + hashedToken), eq(user.getId().toString()), any(Duration.class));
    verify(valueOperations)
        .set(eq("user_refresh_token::" + hashedUserId), eq(hashedToken), any(Duration.class));
  }

  @Test
  @DisplayName("findUserByToken should use hashed token for lookup")
  void findUserByToken_shouldReturnUserIfTokenIsValid() {
    // Given: A valid raw token and user present in repository.
    String rawToken = UUID.randomUUID().toString();
    String hashedToken = hashValue(rawToken);
    Long userId = 1L;
    User user = new User();
    user.setId(userId);

    when(valueOperations.get("refresh_token::" + hashedToken)).thenReturn(userId.toString());
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    // When: Looking up user by token.
    Optional<User> result = refreshTokenService.findUserByToken(rawToken);

    // Then: The user is found and returned.
    assertTrue(result.isPresent());
    assertEquals(userId, result.get().getId());
  }

  @Test
  @DisplayName("findUserByToken should return empty if token is expired or not found")
  void findUserByToken_shouldReturnEmptyIfTokenIsExpiredOrNotFound() {
    // Given: A raw token not present in Redis.
    String rawToken = UUID.randomUUID().toString();
    String hashedToken = hashValue(rawToken);
    when(valueOperations.get("refresh_token::" + hashedToken)).thenReturn(null);

    // When: Looking up user by token.
    Optional<User> result = refreshTokenService.findUserByToken(rawToken);

    // Then: No user is found and repository is not called.
    assertTrue(result.isEmpty());
    verifyNoInteractions(userRepository);
  }

  @Test
  @DisplayName("deleteTokenForUser should use hashed IDs and tokens to delete keys")
  void deleteTokenForUser_shouldDeleteKeysIfTokenExists() {
    // Given: A user with a stored refresh token.
    User user = new User();
    user.setId(1L);
    String hashedUserId = hashValue(user.getId().toString());
    String hashedToken = "hashed-abc-123";

    when(valueOperations.get("user_refresh_token::" + hashedUserId)).thenReturn(hashedToken);

    // When: Deleting the token for the user.
    refreshTokenService.deleteTokenForUser(user);

    // Then: Both token and user keys are deleted from Redis.
    verify(redisTemplate).delete("refresh_token::" + hashedToken);
    verify(redisTemplate).delete("user_refresh_token::" + hashedUserId);
  }

  @Test
  @DisplayName("deleteTokenForUser should do nothing if no token exists")
  void deleteTokenForUser_shouldDoNothingIfNoTokenExists() {
    // Given: A user with no stored refresh token.
    User user = new User();
    user.setId(1L);
    String hashedUserId = hashValue(user.getId().toString());

    when(valueOperations.get("user_refresh_token::" + hashedUserId)).thenReturn(null);

    // When: Deleting the token for the user.
    refreshTokenService.deleteTokenForUser(user);

    // Then: No delete operations are performed on Redis.
    verify(redisTemplate, never()).delete(anyString());
  }
}
