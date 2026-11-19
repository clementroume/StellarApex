package apex.stellar.aldebaran.model.enums;

/**
 * Fundamental movement patterns of a movement or usual name (lot D', D175-D179): the axes of the
 * programming radar and their details. A movement counts once per axis whatever its details; the
 * details are shown on demand (Push / Pull vertical or horizontal, Locomotion, Core directions).
 */
public enum MovementPattern {
  SQUAT(Axis.SQUAT),
  HINGE(Axis.HINGE),
  LUNGE(Axis.LUNGE),
  PUSH_V(Axis.PUSH),
  PUSH_H(Axis.PUSH),
  PULL_V(Axis.PULL),
  PULL_H(Axis.PULL),
  /** Loaded walk. */
  CARRY(Axis.LOCOMOTION),
  /** Walk, run, sled. */
  GAIT(Axis.LOCOMOTION),
  /** Bear crawl, crab walk, handstand walk. */
  CRAWL(Axis.LOCOMOTION),
  /** Swim, bike. */
  CYCLIC(Axis.LOCOMOTION),
  /** Box jump, broad jump, jump rope. */
  JUMP(Axis.LOCOMOTION),
  /** Includes holds working the flexors (plank, hollow). */
  CORE_FLEXION(Axis.CORE),
  /** Includes holds working the extensors (arch). */
  CORE_EXTENSION(Axis.CORE),
  /** Includes anti-rotation (one-arm loads, Pallof). */
  CORE_ROTATION(Axis.CORE),
  /** Includes anti-lateral flexion (side plank, suitcase). */
  CORE_LATERAL_FLEXION(Axis.CORE);

  /** The radar axes, in display order. */
  public enum Axis {
    SQUAT,
    HINGE,
    PUSH,
    PULL,
    LUNGE,
    LOCOMOTION,
    CORE
  }

  private final Axis axis;

  MovementPattern(Axis axis) {
    this.axis = axis;
  }

  /**
   * The radar axis of this pattern.
   *
   * @return The axis.
   */
  public Axis getAxis() {
    return axis;
  }
}
