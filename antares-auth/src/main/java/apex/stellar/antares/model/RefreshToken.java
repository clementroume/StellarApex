package apex.stellar.antares.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;
import org.springframework.data.redis.core.index.Indexed;

/**
 * Redis entity representing a Refresh Token (JWT).
 *
 * <p>Persisted in Redis with a TTL (Time To Live). Used to issue new Access Tokens without
 * requiring user credentials.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RedisHash("refresh_tokens")
public class RefreshToken {

  /** The hashed token value acts as the primary key in Redis. */
  @Id private String id;

  /**
   * Secondary index allowing efficient lookup of all tokens for a specific user. Useful for "Logout
   * All Devices" functionality.
   */
  @Indexed private Long userId;

  /** Expiration time in seconds. Redis automatically evicts the key when this expires. */
  @TimeToLive private Long expiration;
}
