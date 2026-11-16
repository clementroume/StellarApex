package apex.stellar.aldebaran.model.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Positive;

/**
 * What one set of a movement is worth in each unit a WOD line can be written in (D126): a line is
 * divided by the value of its own unit. Carried by a movement and, for a variant whose set differs
 * (Single-under, Handstand Walk, Air Squat), by a usual name. A null value = no set in that unit.
 *
 * @param reps Repetitions of one set.
 * @param meters Metres of one set.
 * @param seconds Seconds of one set.
 * @param calories Calories of one set.
 */
@Embeddable
public record PerSet(
    @Column(name = "reps_per_set") @Positive Integer reps,
    @Column(name = "meters_per_set") @Positive Integer meters,
    @Column(name = "seconds_per_set") @Positive Integer seconds,
    @Column(name = "calories_per_set") @Positive Integer calories) {

  /** No value in any unit. */
  public static final PerSet NONE = new PerSet(null, null, null, null);

  /**
   * Whether no unit has a value.
   *
   * @return True when empty.
   */
  public boolean isEmpty() {
    return reps == null && meters == null && seconds == null && calories == null;
  }

  /**
   * A null-safe view: Hibernate reads an embeddable whose columns are all null as null.
   *
   * @param values The values, possibly null.
   * @return The values, or {@link #NONE}.
   */
  public static PerSet orNone(PerSet values) {
    return values == null ? NONE : values;
  }
}
