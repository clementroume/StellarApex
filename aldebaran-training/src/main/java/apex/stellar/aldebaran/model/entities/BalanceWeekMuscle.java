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

/** Sets of one exact muscle in a stored week: moving (agonist 1, synergist 0.5), holding (stabilizer). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "balance_week_muscles")
public class BalanceWeekMuscle {

  /**
   * Key of a muscle's row.
   *
   * @param userId The athlete.
   * @param weekStart Monday of the week.
   * @param muscleId The muscle.
   */
  @Embeddable
  public record Key(
      @Column(name = "user_id", nullable = false) Long userId,
      @Column(name = "week_start", nullable = false) LocalDate weekStart,
      @Column(name = "muscle_id", nullable = false) Long muscleId) {}

  @EmbeddedId private Key id;

  @Column(name = "moving_sets", nullable = false)
  private double movingSets;

  @Column(name = "holding_sets", nullable = false)
  private double holdingSets;

  @Override
  public boolean equals(Object o) {
    return this == o || (o instanceof BalanceWeekMuscle other && id != null && id.equals(other.id));
  }

  @Override
  public int hashCode() {
    return BalanceWeekMuscle.class.hashCode();
  }
}
