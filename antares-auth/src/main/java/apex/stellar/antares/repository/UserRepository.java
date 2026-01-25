package apex.stellar.antares.repository;

import apex.stellar.antares.config.ApplicationConfig;
import apex.stellar.antares.model.PlatformRole;
import apex.stellar.antares.model.User;
import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for managing {@link User} entities. */
@Repository
public interface UserRepository extends JpaRepository<@NonNull User, @NonNull Long> {

  /**
   * Finds a user by their unique email address. Used primarily during authentication.
   *
   * @param email the email address to search for.
   * @return an {@link Optional} containing the user if found.
   */
  Optional<User> findByEmail(String email);

  /**
   * Efficiently checks if a user with the given email exists.
   *
   * <p>Used during registration to prevent duplicates without loading the full entity.
   *
   * @param email the email to check.
   * @return {@code true} if the email is already taken.
   */
  boolean existsByEmail(String email);

  /**
   * Checks if any user possesses the specified global role.
   *
   * <p>Used by {@link ApplicationConfig} to detect if the default Admin account needs creation.
   *
   * @param platformRole the {@link PlatformRole} to check for.
   * @return {@code true} if at least one user has this role.
   */
  boolean existsByPlatformRole(PlatformRole platformRole);
}
