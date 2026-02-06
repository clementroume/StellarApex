package apex.stellar.antares.service;

import apex.stellar.antares.config.JwtProperties;
import apex.stellar.antares.model.RefreshToken;
import apex.stellar.antares.model.User;
import apex.stellar.antares.repository.jpa.UserRepository;
import apex.stellar.antares.repository.redis.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Service for managing the lifecycle of refresh tokens using Spring Data Redis.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

  private final RefreshTokenRepository refreshTokenRepository;
  private final UserRepository userRepository;
  private final JwtProperties jwtProperties;

  /**
   * Creates a new refresh token for the given user.
   * Enforces "one session per user" by invalidating any existing token.
   *
   * @param user The user.
   * @return The raw token string.
   */
  public String createRefreshToken(User user) {
    // 1. Invalidate previous session
    deleteTokenForUser(user);

    // 2. Generate and Hash token
    String rawToken = UUID.randomUUID().toString();
    String hashedToken = hashValue(rawToken);

    // 3. Save to Redis via Repository
    RefreshToken token = RefreshToken.builder()
        .id(hashedToken)
        .userId(user.getId())
        .expiration(jwtProperties.refreshToken().expiration() / 1000) // Seconds
        .build();

    refreshTokenRepository.save(token);

    return rawToken;
  }

  /**
   * Finds a user associated with a raw refresh token.
   *
   * @param rawToken The raw token from cookie.
   * @return The User if found and valid.
   */
  public Optional<User> findUserByToken(String rawToken) {
    String hashedToken = hashValue(rawToken);

    return refreshTokenRepository.findById(hashedToken)
        .flatMap(refreshToken -> userRepository.findById(refreshToken.getUserId()));
  }

  /**
   * Deletes the refresh token associated with a user.
   *
   * @param user The user.
   */
  public void deleteTokenForUser(User user) {
    // Thanks to @Indexed on userId, we can find the token by user ID efficiently
    refreshTokenRepository.findByUserId(user.getId())
        .ifPresent(refreshTokenRepository::delete);
  }

  private String hashValue(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm not available", e);
    }
  }
}