package apex.stellar.aldebaran.model.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * One block of a session: FR/EN text (warm-up, cool down) or a WOD, either created for the session
 * ({@code standalone = false}) or borrowed from the standalone WODs (scores join that WOD's
 * leaderboard).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "session_blocks")
public class SessionBlock {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "session_id", nullable = false)
  @ToString.Exclude
  private Session session;

  @Column(name = "order_index", nullable = false)
  private Integer orderIndex;

  @Enumerated(EnumType.STRING)
  @Column(name = "kind", nullable = false, length = 20)
  private Kind kind;

  @Column(name = "label", length = 30)
  private String label;

  /** Thematic summary, shown to members in advance ("Snatch lourd EMOM 3/4"). */
  @Column(name = "theme_en", length = 80)
  private String themeEn;

  @Column(name = "theme_fr", length = 80)
  private String themeFr;

  /** Offered on top of the session (accessory, finisher...). */
  @Column(name = "optional", nullable = false)
  @Builder.Default
  private boolean optional = false;

  @Enumerated(EnumType.STRING)
  @Column(name = "stimulus", length = 20)
  private Stimulus stimulus;

  @Column(name = "planned_minutes")
  private Integer plannedMinutes;

  @Column(name = "text_en", columnDefinition = "TEXT")
  private String textEn;

  @Column(name = "text_fr", columnDefinition = "TEXT")
  private String textFr;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "wod_id")
  @ToString.Exclude
  private Wod wod;

  /**
   * Indicates whether the block borrows a standalone WOD (Fran) instead of owning its WOD.
   *
   * @return {@code true} for a borrowed WOD.
   */
  public boolean isBorrowed() {
    return wod != null && wod.isStandalone();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    return id != null && id.equals(((SessionBlock) o).id);
  }

  @Override
  public int hashCode() {
    return id != null ? id.hashCode() : 0;
  }

  /** Part of the session. A WOD session always has a SKILL block and a WOD block. */
  public enum Kind {
    WARM_UP,
    SKILL,
    WOD,
    ACCESSORY,
    FINISHER,
    COOL_DOWN;

    /**
     * Indicates whether the block is FR/EN text only (no WOD, left out of the muscle balance).
     *
     * @return {@code true} for warm-up and cool down.
     */
    public boolean isText() {
      return this == WARM_UP || this == COOL_DOWN;
    }
  }

  /** Kind of effort of the block (weekly review per stimulus). */
  public enum Stimulus {
    STRENGTH,
    POWER,
    SKILL,
    AEROBIC,
    THRESHOLD,
    ANAEROBIC,
    MIXED,
    MOBILITY
  }
}
