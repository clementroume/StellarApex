package apex.stellar.aldebaran.model.enums;

/** Scope of the WOD list view. */
public enum WodScope {
  /** Every WOD the user can read. */
  ALL,
  /** Personal WODs authored by the user (not attached to a gym). */
  MINE,
  /** WODs of the user's member gyms (optionally a single one). */
  GYM,
  /** Public WODs. */
  PUBLIC
}
