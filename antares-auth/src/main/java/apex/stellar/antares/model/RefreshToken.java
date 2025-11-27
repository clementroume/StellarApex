package apex.stellar.antares.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;
import org.springframework.data.redis.core.index.Indexed;

/**
 * Redis entity representing a Refresh Token.
 *
 * <p>Unlike JPA entities, this object lives exclusively in Redis. The @RedisHash annotation marks
 * it for Spring Data Redis repositories. The timeToLive field allows dynamic expiration per token.
 */
@Getter
@Setter
@Builder
@RedisHash("refresh_tokens") // Prefix for Redis keys
public class RefreshToken {

  @Id private String id; // This will store the Hashed Token

  @Indexed // Creates a secondary index to allow findByUserId and deleteByUserId
  private Long userId;

  @TimeToLive // Expiration in seconds, handled automatically by Redis
  private Long expiration;
}
