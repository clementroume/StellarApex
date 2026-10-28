package apex.stellar.antares.service;

import apex.stellar.antares.model.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Single-use tokens mailed to a user (password reset, e-mail verification), stored in Redis.
 *
 * <p>The raw token (256 random bits) only travels in the mailed link; Redis keeps its SHA-256 hash
 * with the user id and the address it was sent to, and expires it. Consuming a token deletes it
 * atomically, so it works once. Issuing a new token for a user replaces the previous one. A token
 * is bound to the address it was mailed to: once the user's email changes, it is refused.
 */
@Service
@RequiredArgsConstructor
public class AccountTokenService {

  static final String TOKEN_PREFIX = "account_token:";
  static final String USER_PREFIX = "account_token_user:";

  private static final SecureRandom RANDOM = new SecureRandom();

  private final StringRedisTemplate redisTemplate;

  @Value("${application.security.password-reset.token-validity:PT30M}")
  private Duration passwordResetValidity;

  @Value("${application.security.email-verification.token-validity:PT48H}")
  private Duration emailVerificationValidity;

  /** What a token allows. */
  public enum Purpose {
    PASSWORD_RESET,
    EMAIL_VERIFICATION
  }

  /** The user and address a consumed token was issued for. */
  public record TokenOwner(Long userId, String email) {}

  /**
   * Issues a token for the user, replacing any previous token with the same purpose.
   *
   * @param user The user (its current email is bound to the token).
   * @param purpose What the token allows.
   * @return The raw token, to put in the mailed link only.
   */
  public String issue(User user, Purpose purpose) {
    revoke(user.getId(), purpose);
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    String hash = hash(raw);
    Duration validity = validity(purpose);
    redisTemplate
        .opsForValue()
        .set(tokenKey(purpose, hash), user.getId() + ":" + user.getEmail(), validity);
    redisTemplate.opsForValue().set(userKey(purpose, user.getId()), hash, validity);
    return raw;
  }

  /**
   * Consumes a token: returns its owner and deletes it, so it can be used once.
   *
   * @param purpose What the token must allow.
   * @param rawToken The token from the link.
   * @return The owner, or empty for an unknown, expired, used or malformed token.
   */
  public Optional<TokenOwner> consume(Purpose purpose, String rawToken) {
    if (rawToken == null || rawToken.isBlank() || rawToken.length() > 100) {
      return Optional.empty();
    }
    String value = redisTemplate.opsForValue().getAndDelete(tokenKey(purpose, hash(rawToken)));
    if (value == null) {
      return Optional.empty();
    }
    int separator = value.indexOf(':');
    try {
      Long userId = Long.parseLong(value.substring(0, separator));
      redisTemplate.delete(userKey(purpose, userId));
      return Optional.of(new TokenOwner(userId, value.substring(separator + 1)));
    } catch (RuntimeException e) {
      return Optional.empty();
    }
  }

  /**
   * Deletes the user's pending token with this purpose, if any.
   *
   * @param userId The user.
   * @param purpose The token purpose.
   */
  public void revoke(Long userId, Purpose purpose) {
    String previous = redisTemplate.opsForValue().getAndDelete(userKey(purpose, userId));
    if (previous != null) {
      redisTemplate.delete(tokenKey(purpose, previous));
    }
  }

  /**
   * How long a token with this purpose stays valid.
   *
   * @param purpose The token purpose.
   * @return The validity.
   */
  public Duration validity(Purpose purpose) {
    return purpose == Purpose.PASSWORD_RESET ? passwordResetValidity : emailVerificationValidity;
  }

  private static String tokenKey(Purpose purpose, String hash) {
    return TOKEN_PREFIX + purpose.name() + ":" + hash;
  }

  private static String userKey(Purpose purpose, Long userId) {
    return USER_PREFIX + purpose.name() + ":" + userId;
  }

  private static String hash(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm not available", e);
    }
  }
}
