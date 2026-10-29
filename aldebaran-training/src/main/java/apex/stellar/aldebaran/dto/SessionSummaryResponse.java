package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.Session.Discipline;
import apex.stellar.aldebaran.model.entities.Session.SessionType;
import apex.stellar.aldebaran.model.entities.Session.VisibilityPolicy;
import apex.stellar.aldebaran.model.entities.SessionBlock.Kind;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Frame of a session for lists (week planning, Today page): never any block content.
 *
 * @param contentVisible The user reads the content.
 * @param loggedByMe The user logged the session.
 */
public record SessionSummaryResponse(
    Long id,
    SessionType type,
    Discipline discipline,
    LocalDate date,
    LocalDate endDate,
    String title,
    Long gymId,
    Long authorId,
    VisibilityPolicy visibilityPolicy,
    Instant contentVisibleFrom,
    List<BlockFrame> blocks,
    boolean contentVisible,
    boolean loggedByMe) {

  /**
   * Frame of a block: what members know in advance.
   *
   * @param kind Kind of block.
   * @param optional Offered on top of the session.
   * @param themeEn Thematic summary (EN), or null.
   * @param themeFr Thematic summary (FR), or null.
   */
  public record BlockFrame(Kind kind, boolean optional, String themeEn, String themeFr) {}
}
