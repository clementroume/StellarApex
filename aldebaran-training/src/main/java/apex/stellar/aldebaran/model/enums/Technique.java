package apex.stellar.aldebaran.model.enums;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Defines specific variations, styles, or modifications applied to a standard movement. */
@Getter
@RequiredArgsConstructor
public enum Technique {

  // --- Execution style & Tempo ---
  STRICT(TechniqueCategory.STYLE),
  KIPPING(TechniqueCategory.STYLE),
  BUTTERFLY(TechniqueCategory.STYLE),
  TEMPO(TechniqueCategory.STYLE),
  PAUSED(TechniqueCategory.STYLE),
  NEGATIVES(TechniqueCategory.STYLE),
  DEAD_STOP(TechniqueCategory.STYLE),
  HAND_RELEASE(TechniqueCategory.STYLE),
  TOUCH_AND_GO(TechniqueCategory.STYLE),
  JUMPING(TechniqueCategory.STYLE),
  PUSH(TechniqueCategory.STYLE),
  RUSSIAN(TechniqueCategory.STYLE),
  AMERICAN(TechniqueCategory.STYLE),
  STEP(TechniqueCategory.STYLE),

  // --- Reception (clean, snatch, jerk) ---
  MUSCLE(TechniqueCategory.RECEPTION),
  POWER(TechniqueCategory.RECEPTION),
  SQUAT(TechniqueCategory.RECEPTION),
  SPLIT(TechniqueCategory.RECEPTION),

  // --- Amplitude ---
  CHEST_TO_BAR(TechniqueCategory.AMPLITUDE),
  KNEE_RAISE(TechniqueCategory.AMPLITUDE),
  KNEES_TO_ELBOWS(TechniqueCategory.AMPLITUDE),
  TOES_TO_BAR(TechniqueCategory.AMPLITUDE),
  HALF(TechniqueCategory.AMPLITUDE),

  // --- Rope rotations ---
  SINGLE_UNDER(TechniqueCategory.ROTATIONS),
  DOUBLE_UNDER(TechniqueCategory.ROTATIONS),
  TRIPLE_UNDER(TechniqueCategory.ROTATIONS),

  // --- Grip & Stance ---
  CLOSE_GRIP(TechniqueCategory.GRIP_STANCE),
  WIDE_GRIP(TechniqueCategory.GRIP_STANCE),
  SNATCH_GRIP(TechniqueCategory.GRIP_STANCE),
  HAMMER(TechniqueCategory.GRIP_STANCE),
  CONVENTIONAL(TechniqueCategory.GRIP_STANCE),
  SUMO(TechniqueCategory.GRIP_STANCE),
  CLOSE_STANCE(TechniqueCategory.GRIP_STANCE),

  // --- Starting Position ---
  HANG(TechniqueCategory.STARTING_POSITION),
  HIGH_HANG(TechniqueCategory.STARTING_POSITION),
  LOW_HANG(TechniqueCategory.STARTING_POSITION),
  FROM_BLOCKS(TechniqueCategory.STARTING_POSITION),
  DEFICIT(TechniqueCategory.STARTING_POSITION),
  TO_PLATFORM(TechniqueCategory.STARTING_POSITION),
  FEET_ELEVATED(TechniqueCategory.STARTING_POSITION),
  CHEST_TO_WALL(TechniqueCategory.STARTING_POSITION),
  FREESTANDING(TechniqueCategory.STARTING_POSITION),
  BOX(TechniqueCategory.STARTING_POSITION),

  // --- Load position ---
  FRONT_RACK(TechniqueCategory.LOAD_POSITION),
  OVERHEAD(TechniqueCategory.LOAD_POSITION),
  BEHIND_THE_NECK(TechniqueCategory.LOAD_POSITION),
  BACK_RACK(TechniqueCategory.LOAD_POSITION),
  HIGH_BAR(TechniqueCategory.LOAD_POSITION),
  LOW_BAR(TechniqueCategory.LOAD_POSITION),
  BEAR_HUG(TechniqueCategory.LOAD_POSITION),
  ZERCHER(TechniqueCategory.LOAD_POSITION),
  ON_SHOULDER(TechniqueCategory.LOAD_POSITION),
  OVER_SHOULDER(TechniqueCategory.LOAD_POSITION),
  GOBLET(TechniqueCategory.LOAD_POSITION),
  FARMERS_CARRY(TechniqueCategory.LOAD_POSITION),

  // --- Body Position ---
  SEATED(TechniqueCategory.BODY_POSITION),
  INCLINED(TechniqueCategory.BODY_POSITION),
  DECLINED(TechniqueCategory.BODY_POSITION),
  L_SIT(TechniqueCategory.BODY_POSITION),
  TUCK(TechniqueCategory.BODY_POSITION),
  PIKE(TechniqueCategory.BODY_POSITION),
  KNEES(TechniqueCategory.BODY_POSITION),
  ROCK(TechniqueCategory.BODY_POSITION),

  // --- Laterality ---
  ALTERNATING(TechniqueCategory.LATERALITY),
  ONE_ARM(TechniqueCategory.LATERALITY),
  SINGLE_LEG(TechniqueCategory.LATERALITY),
  DOUBLE(TechniqueCategory.LATERALITY),

  // --- Movement pattern / Direction ---
  BACKWARD(TechniqueCategory.DIRECTION),
  LATERAL(TechniqueCategory.DIRECTION),
  FACING(TechniqueCategory.DIRECTION),
  WALK(TechniqueCategory.DIRECTION),
  CROSSOVER(TechniqueCategory.DIRECTION),
  CROSS_BODY(TechniqueCategory.DIRECTION),
  CRAWL(TechniqueCategory.DIRECTION),
  BACKSTROKE(TechniqueCategory.DIRECTION),
  BREASTSTROKE(TechniqueCategory.DIRECTION),
  OVER(TechniqueCategory.DIRECTION),
  SHUTTLE(TechniqueCategory.DIRECTION),

  // --- Specific modifiers ---
  LEGLESS(TechniqueCategory.MODIFIER),
  WEIGHTED(TechniqueCategory.MODIFIER),
  ASSISTED(TechniqueCategory.MODIFIER),
  PIROUETTE(TechniqueCategory.MODIFIER);

  private final TechniqueCategory category;

  /** Values a line cannot combine: one at most from each set. */
  private static final List<Set<Technique>> EXCLUSIVE_SETS =
      List.of(
          // Execution
          EnumSet.of(STRICT, KIPPING, BUTTERFLY, JUMPING, NEGATIVES),
          EnumSet.of(RUSSIAN, AMERICAN),
          EnumSet.of(TOUCH_AND_GO, DEAD_STOP),
          // Reception, amplitude, rope
          EnumSet.of(MUSCLE, POWER, SQUAT, SPLIT),
          EnumSet.of(CHEST_TO_BAR, KNEE_RAISE, KNEES_TO_ELBOWS, TOES_TO_BAR, HALF),
          EnumSet.of(SINGLE_UNDER, DOUBLE_UNDER, TRIPLE_UNDER),
          // Grip, stance
          EnumSet.of(CLOSE_GRIP, WIDE_GRIP, SNATCH_GRIP),
          EnumSet.of(CONVENTIONAL, SUMO, CLOSE_STANCE),
          // Start: a deficit combines with a handstand or a raised foot position
          EnumSet.of(HANG, HIGH_HANG, LOW_HANG, FROM_BLOCKS, DEFICIT, TO_PLATFORM, BOX),
          EnumSet.of(CHEST_TO_WALL, FREESTANDING),
          // Load position: the high or low bar details the back rack, it is no other position
          EnumSet.of(
              FRONT_RACK, OVERHEAD, BEHIND_THE_NECK, BACK_RACK, BEAR_HUG, ZERCHER, ON_SHOULDER,
              OVER_SHOULDER, GOBLET, FARMERS_CARRY),
          EnumSet.of(
              FRONT_RACK, OVERHEAD, BEHIND_THE_NECK, HIGH_BAR, LOW_BAR, BEAR_HUG, ZERCHER,
              ON_SHOULDER, OVER_SHOULDER, GOBLET, FARMERS_CARRY),
          // Body
          EnumSet.of(INCLINED, DECLINED, PIKE),
          EnumSet.of(TUCK, PIKE, KNEES),
          // Laterality
          EnumSet.of(ALTERNATING, ONE_ARM, SINGLE_LEG, DOUBLE),
          // Direction
          EnumSet.of(BACKWARD, LATERAL, CROSSOVER),
          EnumSet.of(FACING, LATERAL),
          EnumSet.of(CRAWL, BACKSTROKE, BREASTSTROKE),
          // Modifiers
          EnumSet.of(WEIGHTED, ASSISTED));

  /**
   * The sets of values a line cannot combine.
   *
   * @return The sets.
   */
  public static List<Set<Technique>> exclusiveSets() {
    return EXCLUSIVE_SETS;
  }

  /** Technique Category Inner Enum: the "type" of a technique. */
  public enum TechniqueCategory {
    STYLE,
    RECEPTION,
    AMPLITUDE,
    ROTATIONS,
    GRIP_STANCE,
    STARTING_POSITION,
    LOAD_POSITION,
    BODY_POSITION,
    LATERALITY,
    DIRECTION,
    MODIFIER
  }
}
