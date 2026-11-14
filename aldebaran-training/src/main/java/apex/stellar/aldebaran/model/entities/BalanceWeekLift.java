package apex.stellar.aldebaran.model.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Strength sets of one lift in a stored week (a base movement, or a complex under its name, D124). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "balance_week_lifts")
public class BalanceWeekLift {

  /**
   * Key of a lift's row.
   *
   * @param userId The athlete.
   * @param weekStart Monday of the week.
   * @param label Name of the lift.
   */
  @Embeddable
  public record Key(
      @Column(name = "user_id", nullable = false) Long userId,
      @Column(name = "week_start", nullable = false) LocalDate weekStart,
      @Column(name = "label", nullable = false, length = 200) String label) {}

  @EmbeddedId private Key id;

  /** The base movement, null for a complex. */
  @Column(name = "movement_id")
  private Long movementId;

  @Column(nullable = false)
  private double sets;

  /** One of the sets was at 80 % of the 1RM of its day. */
  @Column(name = "near_record", nullable = false)
  private boolean nearRecord;

  @Override
  public boolean equals(Object o) {
    return this == o || (o instanceof BalanceWeekLift other && id != null && id.equals(other.id));
  }

  @Override
  public int hashCode() {
    return BalanceWeekLift.class.hashCode();
  }
}
