package apex.stellar.antares.repository.jpa;

import apex.stellar.antares.config.ApplicationConfig; // This import will be updated later
import apex.stellar.antares.model.GymRole;
import apex.stellar.antares.model.Membership.MembershipStatus;
import apex.stellar.antares.model.PlatformRole;
import apex.stellar.antares.model.User;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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

  /**
   * Finds the ids of users whose first name, last name or email matches a LIKE pattern
   * (case-insensitive, backslash as escape character), ordered by last name.
   *
   * <p>When {@code anyMembership} is false, only users holding at least one membership matching the
   * optional gym, role and status filters are returned.
   *
   * @param pattern a lower-case LIKE pattern (e.g. {@code %doe%}).
   * @param anyMembership true to ignore the membership filters.
   * @param gymId the membership gym, or null.
   * @param role the membership role, or null.
   * @param status the membership status, or null.
   * @param pageable the requested page.
   * @return the matching user ids, in display order.
   */
  @Query(
      "SELECT u.id FROM User u"
          + " WHERE (lower(u.firstName) LIKE :pattern ESCAPE '\\'"
          + " OR lower(u.lastName) LIKE :pattern ESCAPE '\\'"
          + " OR lower(u.email) LIKE :pattern ESCAPE '\\')"
          + " AND (:anyMembership = true OR EXISTS (SELECT 1 FROM Membership m WHERE m.user = u"
          + " AND (:gymId IS NULL OR m.gym.id = :gymId)"
          + " AND (:role IS NULL OR m.gymRole = :role)"
          + " AND (:status IS NULL OR m.status = :status)))"
          + " ORDER BY u.lastName, u.firstName, u.id")
  Slice<Long> searchIds(
      String pattern,
      boolean anyMembership,
      Long gymId,
      GymRole role,
      MembershipStatus status,
      Pageable pageable);

  /**
   * Loads users with their memberships and gyms in a single query.
   *
   * @param ids the user ids.
   * @return the users, in no particular order.
   */
  @EntityGraph(attributePaths = {"memberships", "memberships.gym"})
  List<User> findWithMembershipsByIdIn(Collection<Long> ids);
}
