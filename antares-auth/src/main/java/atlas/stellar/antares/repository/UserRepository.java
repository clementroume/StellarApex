package atlas.stellar.antares.repository;

import atlas.stellar.antares.config.ApplicationConfig;
import atlas.stellar.antares.model.Role;
import atlas.stellar.antares.model.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for the {@link User} entity.
 *
 * <p>This interface provides the mechanism for all data access operations related to users. It
 * abstracts the database interactions and provides both standard CRUD functionality and
 * custom-defined query methods.
 */
public interface UserRepository extends JpaRepository<User, Long> {

  /**
   * Finds a user by their unique email address.
   *
   * <p>This is a "derived query method"; Spring Data JPA automatically generates the
   * implementation. It is essential for the authentication process.
   *
   * @param email The email address to search for.
   * @return An {@link Optional} containing the found {@link User}, or empty otherwise.
   */
  Optional<User> findByEmail(String email);

  /**
   * Checks if a user with the specified role exists.
   *
   * <p>Used by the {@link ApplicationConfig} to determine if the default admin user needs to be
   * created on startup.
   *
   * @param role The role to check for.
   * @return true if at least one user has this role, false otherwise.
   */
  boolean existsByRole(Role role);
}
