package apex.stellar.aldebaran.model.enums;

/** What a WOD line of a movement must state (enforced once, in {@code CatalogRules}). */
public enum RequiredRule {
  /** A load position as soon as the line has an equipment (Squat). */
  LOAD_POSITION_IF_EQUIPMENT,
  /** An amplitude (Hanging Raise). */
  AMPLITUDE,
  /** A number of rope rotations (Jump Rope). */
  ROTATIONS,
  /** Russian or American (Kettlebell Swing). */
  SWING_STYLE,
  /** An equipment (Muscle-up: rings or bar). */
  EQUIPMENT
}
