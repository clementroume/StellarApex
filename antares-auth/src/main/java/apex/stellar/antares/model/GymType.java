package apex.stellar.antares.model;

/** Kind of gym, as searched by users: a physical box or a virtual programming (program). */
public enum GymType {
  /** Physical CrossFit box ({@code isProgramming = false}). */
  BOX,

  /** Virtual programming ({@code isProgramming = true}). */
  PROGRAM
}
