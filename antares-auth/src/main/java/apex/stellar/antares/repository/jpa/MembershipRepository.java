package apex.stellar.antares.repository.jpa;

import apex.stellar.antares.model.Gym;
import apex.stellar.antares.model.Membership;
import apex.stellar.antares.model.Membership.MembershipStatus;
import apex.stellar.antares.model.User;
import java.util.Collection;
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
  @Query("SELECT m FROM Membership m WHERE m.gym.id = :gymId")
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
  @Query("SELECT m FROM Membership m WHERE m.gym.id = :gymId AND m.status = :status")
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

  /**
   * Retrieves the holder membership(s) of a gym (OWNER or PROGRAMMER; at most one, enforced by a
   * unique index).
   *
   * @param gymId the unique identifier of the gym.
   * @return the holder memberships.
   */
  @Query(
      "SELECT m FROM Membership m WHERE m.gym.id = :gymId"
          + " AND m.gymRole IN (apex.stellar.antares.model.GymRole.OWNER,"
          + " apex.stellar.antares.model.GymRole.PROGRAMMER)")
  List<Membership> findHoldersByGymId(Long gymId);

  /**
   * Retrieves the holder memberships (with their user) of several gyms.
   *
   * @param gymIds the gyms.
   * @return the holder memberships of these gyms.
   */
  @EntityGraph(attributePaths = {"user"})
  @Query(
      "SELECT m FROM Membership m WHERE m.gym.id IN :gymIds"
          + " AND m.gymRole IN (apex.stellar.antares.model.GymRole.OWNER,"
          + " apex.stellar.antares.model.GymRole.PROGRAMMER)")
  List<Membership> findHoldersByGymIdIn(Collection<Long> gymIds);

  /**
   * Counts the memberships of several gyms having a given status.
   *
   * @param gymIds the gyms.
   * @param status the membership status to count.
   * @return pairs {@code [gymId, count]}.
   */
  @Query(
      "SELECT m.gym.id, count(m) FROM Membership m WHERE m.gym.id IN :gymIds"
          + " AND m.status = :status GROUP BY m.gym.id")
  List<Object[]> countByGymIdInAndStatus(Collection<Long> gymIds, MembershipStatus status);

  /**
   * Tells whether a user holds a gym that still waits for admin approval (at most one creation
   * request per user).
   *
   * @param userId the user.
   * @return {@code true} if the user holds a gym in {@code PENDING_APPROVAL}.
   */
  @Query(
      "SELECT count(m) > 0 FROM Membership m WHERE m.user.id = :userId"
          + " AND m.gymRole IN (apex.stellar.antares.model.GymRole.OWNER,"
          + " apex.stellar.antares.model.GymRole.PROGRAMMER)"
          + " AND m.gym.status = apex.stellar.antares.model.Gym.GymStatus.PENDING_APPROVAL")
  boolean existsPendingGymRequest(Long userId);
}
