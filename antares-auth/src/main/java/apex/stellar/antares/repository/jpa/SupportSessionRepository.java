package apex.stellar.antares.repository.jpa;

import apex.stellar.antares.model.SupportSession;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for {@link SupportSession} audit records. */
@Repository
public interface SupportSessionRepository extends JpaRepository<SupportSession, Long> {

  /**
   * Finds the sessions of an admin that are neither ended nor expired.
   *
   * @param adminId the admin's user id.
   * @param now the current instant.
   * @return the active sessions (at most one in practice).
   */
  @Query(
      "SELECT s FROM SupportSession s WHERE s.adminId = :adminId AND s.endedAt IS NULL"
          + " AND s.expiresAt > :now")
  List<SupportSession> findActiveByAdminId(Long adminId, Instant now);

  /**
   * Finds the users an admin acted as most recently, latest first (one entry per user).
   *
   * @param adminId the admin's user id.
   * @param pageable the page (used as a limit).
   * @return the target user ids.
   */
  @Query(
      "SELECT s.targetUserId FROM SupportSession s WHERE s.adminId = :adminId"
          + " GROUP BY s.targetUserId ORDER BY MAX(s.startedAt) DESC")
  List<Long> findRecentTargetIds(Long adminId, Pageable pageable);
}
