package apex.stellar.antares.repository;

import apex.stellar.antares.model.Gym;
import apex.stellar.antares.model.Membership;
import apex.stellar.antares.model.Membership.MembershipStatus;
import apex.stellar.antares.model.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for managing {@link Membership} entities.
 *
 * <p>This repository leverages {@link EntityGraph} annotations to optimize fetching strategies and
 * prevent N+1 Select issues when retrieving associated {@link User} or {@link Gym} entities.
 */
@Repository
public interface MembershipRepository extends JpaRepository<Membership, Long> {

  /**
   * Retrieves all memberships associated with a specific gym.
   *
   * <p>Uses an {@code EntityGraph} to eagerly fetch the associated {@code User} entity, optimizing
   * performance for member lists (Staff/Admin views).
   *
   * @param gymId the unique identifier of the gym.
   * @return a list of memberships with initialized User data.
   */
  @EntityGraph(attributePaths = {"user"})
  List<Membership> findByGymId(Long gymId);

  /**
   * Retrieves memberships for a specific gym filtered by their status (e.g., PENDING).
   *
   * <p>Uses an {@code EntityGraph} to eagerly fetch the associated {@code User} entity.
   *
   * @param gymId the unique identifier of the gym.
   * @param status the membership status to filter by.
   * @return a list of matching memberships with initialized User data.
   */
  @EntityGraph(attributePaths = {"user"})
  List<Membership> findByGymIdAndStatus(Long gymId, MembershipStatus status);

  /**
   * Retrieves a specific membership by User ID and Gym ID.
   *
   * <p>This method does <b>not</b> trigger eager loading of associations, keeping the query
   * lightweight for security checks (e.g., verifying access rights).
   *
   * @param userId the unique identifier of the user.
   * @param gymId the unique identifier of the gym.
   * @return an {@link Optional} containing the membership if found.
   */
  @Query("SELECT m FROM Membership m WHERE m.user.id = :userId AND m.gym.id = :gymId")
  Optional<Membership> findByUserIdAndGymId(Long userId, Long gymId);

  /**
   * Retrieves all memberships held by a specific user (i.e., "My Gyms").
   *
   * <p>Uses an {@code EntityGraph} to eagerly fetch the associated {@code Gym} entity, allowing the
   * user to see details of the gyms they belong to without extra queries.
   *
   * @param userId the unique identifier of the user.
   * @return a list of memberships with initialized Gym data.
   */
  @EntityGraph(attributePaths = {"gym"})
  List<Membership> findByUserId(Long userId);

  /**
   * Efficiently checks if a membership exists for a given user and gym.
   *
   * <p>Returns a boolean without loading the entity into the persistence context.
   *
   * @param userId the unique identifier of the user.
   * @param gymId the unique identifier of the gym.
   * @return {@code true} if a membership exists, {@code false} otherwise.
   */
  @Query("SELECT count(m) > 0 FROM Membership m WHERE m.user.id = :userId AND m.gym.id = :gymId")
  boolean existsByUserIdAndGymId(Long userId, Long gymId);
}
