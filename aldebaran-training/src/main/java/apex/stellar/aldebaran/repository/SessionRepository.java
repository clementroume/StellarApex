package apex.stellar.aldebaran.repository;

import apex.stellar.aldebaran.model.entities.Session;
import apex.stellar.aldebaran.model.entities.Session.SessionType;
import java.time.LocalDate;
import java.util.Collection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository of training sessions. */
@Repository
public interface SessionRepository extends JpaRepository<Session, Long> {

  /**
   * Bounds of an open date range: PostgreSQL cannot type a null date parameter, so callers pass
   * these instead of null.
   */
  LocalDate NO_START = LocalDate.of(1, 1, 1);

  LocalDate NO_END = LocalDate.of(9999, 12, 31);

  /**
   * Retrieves the sessions whose frame the user reads, with optional combinable filters.
   *
   * <p><b>Security clause:</b> personal session of the user OR session of one of the user's gyms
   * (any role: the frame of a gym session is known in advance; its content is filtered per session
   * by the service). A platform admin lists another gym's sessions only when asking for that gym
   * ({@code gymId}): lists stay those of the admin's own gyms (Today, week, journal), the admin
   * bypass serving support on a chosen gym or session.
   *
   * <p><b>Scopes:</b> {@code ALL} everything listed, {@code MINE} the user's personal sessions,
   * {@code GYM} the sessions of the member gyms (admins: the requested gym too), narrowed to
   * {@code gymId}.
   *
   * <p>{@code from} / {@code to} keep the sessions proposed on at least one day of the range (a
   * specialty covers its whole week); never null ({@link #NO_START} / {@link #NO_END}).
   *
   * @return A slice of sessions, by date then creation.
   */
  @Query(
      """
      SELECT s FROM Session s WHERE
      ((s.gymId IS NULL AND s.authorId = :userId)
         OR s.gymId IN :memberGymIds
         OR (:isAdmin = true AND s.gymId = :gymId))
      AND s.endDate >= :from
      AND s.date <= :to
      AND (:type IS NULL OR s.type = :type)
      AND (:scope = 'ALL'
         OR (:scope = 'MINE' AND s.gymId IS NULL AND s.authorId = :userId)
         OR (:scope = 'GYM'
             AND s.gymId IS NOT NULL
             AND (:gymId IS NULL OR s.gymId = :gymId)))
      """)
  Slice<Session> findSecure(
      @Param("scope") String scope,
      @Param("gymId") Long gymId,
      @Param("from") LocalDate from,
      @Param("to") LocalDate to,
      @Param("type") SessionType type,
      @Param("userId") Long userId,
      @Param("memberGymIds") Collection<Long> memberGymIds,
      @Param("isAdmin") boolean isAdmin,
      Pageable pageable);
}
