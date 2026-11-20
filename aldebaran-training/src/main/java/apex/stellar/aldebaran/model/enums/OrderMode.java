package apex.stellar.aldebaran.model.enums;

/** What a session-order proposal may move within a week (lot D', D168). */
public enum OrderMode {
  /** The WOD parts (WOD, Accessory, Finisher) change day; the Skills stay. */
  WOD_ONLY,
  /** Whole sessions change day, Skill and WOD together. */
  WHOLE_SESSIONS,
  /** Skills and WOD parts change day separately. */
  WOD_AND_SKILLS
}
