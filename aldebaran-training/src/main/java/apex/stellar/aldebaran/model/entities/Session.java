package apex.stellar.aldebaran.model.entities;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.BatchSize;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A training session (lot B): an ordered container of blocks, proposed on a day (WOD session, run
 * in several slots of the day) or a week (specialty session, {@code date} = Monday). The same blocks
 * at two dates are two sessions.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sessions")
@EntityListeners(AuditingEntityListener.class)
public class Session {

  // --- Identification ---
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version
  @Column(name = "version")
  private Long version;

  @Enumerated(EnumType.STRING)
  @Column(name = "session_type", nullable = false, length = 20)
  private SessionType type;

  @Enumerated(EnumType.STRING)
  @Column(name = "discipline", nullable = false, length = 20)
  private Discipline discipline;

  /** Day of a WOD session, Monday of the week of a specialty session. */
  @Column(name = "date", nullable = false)
  private LocalDate date;

  /** Last day the session is proposed on (the day itself, or the Sunday of the week). */
  @Column(name = "end_date", nullable = false)
  private LocalDate endDate;

  @Column(name = "title", length = 100)
  private String title;

  @Column(name = "description_en", columnDefinition = "TEXT")
  private String descriptionEn;

  @Column(name = "description_fr", columnDefinition = "TEXT")
  private String descriptionFr;

  // --- Ownership ---
  /** Gym (box or program) of the session, or null for a personal session (author only). */
  @Column(name = "gym_id")
  private Long gymId;

  @Column(name = "author_id", nullable = false)
  private Long authorId;

  // --- Visibility of the content for the gym's athletes ---
  @Enumerated(EnumType.STRING)
  @Column(name = "visibility_policy", length = 30)
  private VisibilityPolicy visibilityPolicy;

  /** Instant the content opens to every member, or null (participants only, personal session). */
  @Column(name = "content_visible_from")
  private Instant contentVisibleFrom;

  // --- Blocks ---
  @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("orderIndex ASC")
  @BatchSize(size = 50)
  @Builder.Default
  @ToString.Exclude
  private List<SessionBlock> blocks = new ArrayList<>();

  // --- Audit ---
  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  /**
   * Sets the date of the session, normalised to the Monday of its week for a specialty, and the
   * last day it is proposed on.
   *
   * @param type The session type.
   * @param day Any day of the session (WOD) or of its week (specialty).
   */
  public void schedule(SessionType type, LocalDate day) {
    this.type = type;
    if (type == SessionType.SPECIALTY) {
      this.date = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
      this.endDate = this.date.plusDays(6);
    } else {
      this.date = day;
      this.endDate = day;
    }
  }

  /**
   * Indicates whether the session is proposed on the given day.
   *
   * @param day The day.
   * @return {@code true} between {@code date} and {@code endDate}, inclusive.
   */
  public boolean covers(LocalDate day) {
    return !day.isBefore(date) && !day.isAfter(endDate);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    return id != null && id.equals(((Session) o).id);
  }

  @Override
  public int hashCode() {
    return id != null ? id.hashCode() : 0;
  }

  /** WOD session (a day, several slots) or specialty session (a week). */
  public enum SessionType {
    WOD,
    SPECIALTY
  }

  /**
   * Discipline of the session (filters, planning, weekly review). Class names of the planning
   * (WOD2, Team WOD, Kids...) are tracks of the planning service, not disciplines.
   */
  public enum Discipline {
    CROSSFIT,
    WEIGHTLIFTING,
    STRENGTH,
    GYMNASTICS,
    ENDURANCE,
    HYROX,
    MOBILITY,
    BODYBUILDING,
    STRONGMAN,
    OTHER
  }

  /**
   * When the content of a gym session opens to its athletes (staff always read it). A program only
   * uses {@code ADVANCE} and {@code DAY_OF}.
   */
  public enum VisibilityPolicy {
    /** At an instant chosen by the staff, before the session. */
    ADVANCE,
    /** On the day of the session (a day of its week), at a time chosen by the staff. */
    DAY_OF,
    /** The day after the session (the Monday after a specialty week). */
    AFTER,
    /** As {@code AFTER}, and to each participant from their "Séance faite". */
    AFTER_PARTICIPANTS,
    /** To participants only, from their "Séance faite"; never to the whole gym. */
    PARTICIPANTS_ONLY;

    /**
     * Indicates whether a participant reads the content as soon as they logged the session.
     *
     * @return {@code true} for the participant policies.
     */
    public boolean opensToParticipants() {
      return this == AFTER_PARTICIPANTS || this == PARTICIPANTS_ONLY;
    }

    /**
     * Indicates whether the staff chooses the opening instant.
     *
     * @return {@code true} for {@code ADVANCE} and {@code DAY_OF}.
     */
    public boolean needsInstant() {
      return this == ADVANCE || this == DAY_OF;
    }

    /**
     * Indicates whether a virtual program may use the policy (it sells its programming: no
     * participants, nothing to hide after the fact).
     *
     * @return {@code true} for {@code ADVANCE} and {@code DAY_OF}.
     */
    public boolean allowedForProgram() {
      return needsInstant();
    }
  }
}
