package apex.stellar.antares.repository;

import apex.stellar.antares.model.RefreshToken;
import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * Redis Repository for managing {@link RefreshToken} entities.
 *
 * <p>Leverages Spring Data Redis to handle key-value storage, serialization, and secondary index
 * lookups (e.g., finding tokens by User ID).
 */
@Repository
public interface RefreshTokenRepository
    extends CrudRepository<@NonNull RefreshToken, @NonNull String> {

  /**
   * Retrieves a refresh token using the secondary index on User ID.
   *
   * @param userId the ID of the user.
   * @return an {@link Optional} containing the token if found.
   */
  Optional<RefreshToken> findByUserId(Long userId);
}
