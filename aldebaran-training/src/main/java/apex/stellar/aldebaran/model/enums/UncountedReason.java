package apex.stellar.aldebaran.model.enums;

/** Why a WOD line counts nothing in the weekly balance (D121 rule 5, D126). */
public enum UncountedReason {
  /** Its text (or, without text, its measures) cannot be read. */
  UNREADABLE,
  /** Its unit has no value of one set in the catalog (usual name or movement). */
  NO_SET_VALUE
}
