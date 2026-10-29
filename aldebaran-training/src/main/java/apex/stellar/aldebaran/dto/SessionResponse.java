package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.Session.Discipline;
import apex.stellar.aldebaran.model.entities.Session.SessionType;
import apex.stellar.aldebaran.model.entities.Session.VisibilityPolicy;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A session as seen by the user: descriptions and block contents are null while hidden.
 *
 * @param date Day of a WOD session, Monday of a specialty week.
 * @param endDate Last day the session is proposed on.
 * @param contentVisibleFrom Instant the content opens to the whole gym, or null.
 * @param myLog The user's journal of the session, or null.
 */
public record SessionResponse(
    Long id,
    SessionType type,
    Discipline discipline,
    LocalDate date,
    LocalDate endDate,
    String title,
    String descriptionEn,
    String descriptionFr,
    Long gymId,
    Long authorId,
    VisibilityPolicy visibilityPolicy,
    Instant contentVisibleFrom,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<SessionBlockResponse> blocks,
    SessionCapabilities capabilities,
    SessionLogResponse myLog) {}
