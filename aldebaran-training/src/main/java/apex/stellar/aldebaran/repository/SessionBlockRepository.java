package apex.stellar.aldebaran.repository;

import apex.stellar.aldebaran.model.entities.Session;
import apex.stellar.aldebaran.model.entities.SessionBlock;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository of session blocks. */
@Repository
public interface SessionBlockRepository extends JpaRepository<SessionBlock, Long> {

  /**
   * Checks whether a session block uses the WOD (owned or borrowed): such a WOD cannot be deleted.
   *
   * @param wodId The WOD ID.
   * @return true if a block points at it.
   */
  boolean existsByWodId(Long wodId);

  /**
   * Retrieves the session owning a session WOD ({@code standalone = false}).
   *
   * @param wodId The WOD ID.
   * @return The session, or empty for a standalone WOD.
   */
  @Query(
      "SELECT b.session FROM SessionBlock b WHERE b.wod.id = :wodId AND b.wod.standalone = false")
  Optional<Session> findSessionOwningWod(@Param("wodId") Long wodId);

  /**
   * Checks whether the session has a block with the WOD (owned or borrowed).
   *
   * @param sessionId The session ID.
   * @param wodId The WOD ID.
   * @return true if one of its blocks points at the WOD.
   */
  boolean existsBySessionIdAndWodId(Long sessionId, Long wodId);
}
