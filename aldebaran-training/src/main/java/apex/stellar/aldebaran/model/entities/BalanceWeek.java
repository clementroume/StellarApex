package apex.stellar.aldebaran.model.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One stored week of an athlete's balance (lot D, D120): totals of the week, rebuilt on display
 * when {@code stale} or computed with older rules. Written only through {@code
 * BalanceWeekRepository} (upserts), read through this entity.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "balance_weeks")
public class BalanceWeek {

  /**
   * Key of a stored week.
   *
   * @param userId The athlete.
   * @param weekStart Its Monday.
   */
  @Embeddable
  public record Key(
      @Column(name = "user_id", nullable = false) Long userId,
      @Column(name = "week_start", nullable = false) LocalDate weekStart) {}

  @EmbeddedId private Key id;

  /** Version of the rules the week was computed with ({@code BalanceCalculator.RULES_VERSION}). */
  @Column(name = "rules_version", nullable = false)
  private int rulesVersion;

  /** A write since then asks for a rebuild. */
  @Column(nullable = false)
  private boolean stale;

  /** Equivalent sets of the week (volume histogram). */
  @Column(name = "total_sets", nullable = false)
  private double totalSets;

  @Column(nullable = false)
  private double weightlifting;

  @Column(nullable = false)
  private double gymnastics;

  @Column(nullable = false)
  private double monostructural;

  /** Metcon seconds per time domain. */
  @Column(name = "very_short_s", nullable = false)
  private int veryShortSeconds;

  @Column(name = "short_s", nullable = false)
  private int shortSeconds;

  @Column(name = "medium_s", nullable = false)
  private int mediumSeconds;

  @Column(name = "long_s", nullable = false)
  private int longSeconds;

  @Column(name = "intervals_s", nullable = false)
  private int intervalsSeconds;

  @Column(name = "computed_at")
  private LocalDateTime computedAt;

  @Override
  public boolean equals(Object o) {
    return this == o || (o instanceof BalanceWeek other && id != null && id.equals(other.id));
  }

  @Override
  public int hashCode() {
    return BalanceWeek.class.hashCode();
  }
}
