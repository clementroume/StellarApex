package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.Session.Discipline;
import apex.stellar.aldebaran.model.entities.Session.SessionType;
import apex.stellar.aldebaran.model.entities.SessionBlock.Kind;
import apex.stellar.aldebaran.model.entities.SessionLog.Source;
import java.time.LocalDate;
import java.util.List;

/**
 * Journal of a session done by an athlete.
 *
 * @param date Day the athlete did it.
 * @param blocks Done flag of each block, in block order.
 * @param scores Scores linked to the journal.
 */
public record SessionLogResponse(
    Long id,
    Long userId,
    Long sessionId,
    SessionType sessionType,
    Discipline discipline,
    LocalDate sessionDate,
    String sessionTitle,
    Long gymId,
    LocalDate date,
    Source source,
    List<BlockDone> blocks,
    List<ScoreResponse> scores) {

  /**
   * Done flag of one block.
   *
   * @param blockId The block.
   * @param kind Its kind.
   * @param done Whether the athlete did it.
   */
  public record BlockDone(Long blockId, Kind kind, boolean done) {}

  /**
   * Same journal with other score representations (per-viewer capabilities).
   *
   * @param viewerScores The scores to expose.
   * @return A copy of the journal.
   */
  public SessionLogResponse withScores(List<ScoreResponse> viewerScores) {
    return new SessionLogResponse(
        id, userId, sessionId, sessionType, discipline, sessionDate, sessionTitle, gymId, date,
        source, blocks, viewerScores);
  }
}
