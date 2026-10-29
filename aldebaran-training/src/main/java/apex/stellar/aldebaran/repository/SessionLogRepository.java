package apex.stellar.aldebaran.repository;

import apex.stellar.aldebaran.model.entities.SessionLog;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository of session journals. */
@Repository
public interface SessionLogRepository extends JpaRepository<SessionLog, Long> {

  /**
   * Retrieves the journal of an athlete for a session.
   *
   * @param userId The athlete.
   * @param sessionId The session.
   * @return The journal, if the athlete logged the session.
   */
  Optional<SessionLog> findByUserIdAndSessionId(Long userId, Long sessionId);

  /**
   * Checks whether the athlete participated in the session (has a journal of it).
   *
   * @param userId The athlete.
   * @param sessionId The session.
   * @return true for a participant.
   */
  boolean existsByUserIdAndSessionId(Long userId, Long sessionId);

  /**
   * Checks whether anyone logged the session (it is then locked).
   *
   * @param sessionId The session.
   * @return true if a journal exists.
   */
  boolean existsBySessionId(Long sessionId);

  /**
   * Among the given sessions, those the athlete logged.
   *
   * @param userId The athlete.
   * @param sessionIds The sessions.
   * @return The IDs of the sessions logged by the athlete.
   */
  @Query(
      "SELECT l.session.id FROM SessionLog l WHERE l.userId = :userId"
          + " AND l.session.id IN :sessionIds")
  List<Long> findLoggedSessionIds(
      @Param("userId") Long userId, @Param("sessionIds") Collection<Long> sessionIds);

  /**
   * Retrieves the journal of an athlete, latest first, optionally within a date range.
   *
   * @param userId The athlete.
   * @param from First day ({@code SessionRepository.NO_START} for none).
   * @param to Last day ({@code SessionRepository.NO_END} for none).
   * @param pageable Pagination.
   * @return A slice of journals with their sessions.
   */
  @EntityGraph(attributePaths = {"session"})
  @Query(
      "SELECT l FROM SessionLog l WHERE l.userId = :userId"
          + " AND l.date >= :from AND l.date <= :to"
          + " ORDER BY l.date DESC, l.id DESC")
  Slice<SessionLog> findByUser(
      @Param("userId") Long userId,
      @Param("from") LocalDate from,
      @Param("to") LocalDate to,
      Pageable pageable);
}
