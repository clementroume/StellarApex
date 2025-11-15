package atlas.stellar.antares.service;

import atlas.stellar.antares.config.JwtProperties;
import atlas.stellar.antares.exception.HashingException;
import atlas.stellar.antares.model.User;
import atlas.stellar.antares.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing the lifecycle of refresh tokens using Redis.
 *
 * <p>This service implements a secure storage pattern by hashing tokens and user IDs before storing
 * them in Redis, mitigating the risk of token theft from database access.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

  private static final String REFRESH_TOKEN_PREFIX = "refresh_token::";
  private static final String USER_TOKEN_PREFIX = "user_refresh_token::";
  private final RedisTemplate<String, String> redisTemplate;
  private final UserRepository userRepository;
  private final JwtProperties jwtProperties;

  /**
   * Creates a new refresh token for the given user, deleting any existing token to enforce a "one
   * session at a time" policy.
   *
   * @param user The user for whom to create the refresh token.
   * @return The raw, unhashed refresh token (to be sent in the cookie).
   */
  @Transactional
  public String createRefreshToken(User user) {

    deleteTokenForUser(user);

    String rawToken = UUID.randomUUID().toString();
    String hashedToken = hashValue(rawToken);
    Duration duration = Duration.ofMillis(jwtProperties.refreshToken().expiration());

    redisTemplate
        .opsForValue()
        .set(REFRESH_TOKEN_PREFIX + hashedToken, user.getId().toString(), duration);

    redisTemplate
        .opsForValue()
        .set(USER_TOKEN_PREFIX + hashValue(user.getId().toString()), hashedToken, duration);

    return rawToken;
  }

  /**
   * Finds a user by their raw refresh token.
   *
   * @param rawToken The raw refresh token from the cookie.
   * @return An Optional containing the User if found, or empty if not.
   */
  public Optional<User> findUserByToken(String rawToken) {

    String userId = redisTemplate.opsForValue().get(REFRESH_TOKEN_PREFIX + hashValue(rawToken));

    return userId != null ? userRepository.findById(Long.parseLong(userId)) : Optional.empty();
  }

  /**
   * Deletes all refresh token entries associated with a specific user.
   *
   * @param user The user whose token should be deleted.
   */
  public void deleteTokenForUser(User user) {

    String userKey = USER_TOKEN_PREFIX + hashValue(user.getId().toString());
    String hashedToken = redisTemplate.opsForValue().get(userKey);

    if (hashedToken != null) {
      redisTemplate.delete(REFRESH_TOKEN_PREFIX + hashedToken);
      redisTemplate.delete(userKey);
    }
  }

  /**
   * Hashes a string value using SHA-256 and encodes it in Base64.
   *
   * @param value The string to hash.
   * @return The Base64 encoded SHA-256 hash.
   * @throws HashingException if SHA-256 is not available.
   */
  private String hashValue(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new HashingException("error.hashing.unavailable");
    }
  }
}
