package com.antares.api.repository;

import com.antares.api.model.Role;
import com.antares.api.model.User;
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
   * <p>This is a "derived query method"; Spring Data JPA automatically generates the implementation
   * based on the method name. It's a key part of the authentication process.
   *
   * @param email The email address to search for.
   * @return An {@link Optional} containing the found {@link User} if one exists, or an empty
   *     Optional otherwise.
   */
  Optional<User> findByEmail(String email);

  /**
   * Checks if a user with the specified role exists.
   *
   * @param role The role to check for.
   * @return true if at least one user has this role, false otherwise.
   */
  boolean existsByRole(Role role);
}
