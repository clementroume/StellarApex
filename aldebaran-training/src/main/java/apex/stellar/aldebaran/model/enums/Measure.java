package apex.stellar.aldebaran.model.enums;

import apex.stellar.aldebaran.model.entities.PerSet;
import org.jspecify.annotations.Nullable;

/** The unit a WOD line is written in; a line is divided by the value of one set in it (D126). */
public enum Measure {
  REPS,
  METERS,
  SECONDS,
  CALORIES;

  /**
   * The value of one set in this unit. Repetitions of a movement counted in calories only (a
   * cardio machine written {@code 21-15-9}) read as calories: one calorie = one rep (D121).
   *
   * @param perSet The values of the line.
   * @return The value, or null when the catalog has none.
   */
  public @Nullable Integer of(PerSet perSet) {
    return switch (this) {
      case REPS -> perSet.reps() != null ? perSet.reps() : perSet.calories();
      case METERS -> perSet.meters();
      case SECONDS -> perSet.seconds();
      case CALORIES -> perSet.calories() != null ? perSet.calories() : perSet.reps();
    };
  }
}
