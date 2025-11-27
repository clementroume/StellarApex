package apex.stellar.antares.repository;

import apex.stellar.antares.model.RefreshToken;
import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for managing {@link RefreshToken} in Redis.
 *
 * <p>Spring Data Redis automatically implements these methods, managing serialization, keys, and
 * secondary indexes (like userId).
 */
@Repository
public interface RefreshTokenRepository
    extends CrudRepository<@NonNull RefreshToken, @NonNull String> {

  /**
   * Finds a token by its secondary index (userId), used to enforce the "one session per user"
   * policy.
   */
  Optional<RefreshToken> findByUserId(Long userId);
}
