package apex.stellar.aldebaran.model.enums;

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
  EXPLOSIVE(TechniqueCategory.STYLE),
  JUMPING(TechniqueCategory.STYLE),

  // --- Grip & Stance ---
  CLOSE_GRIP(TechniqueCategory.GRIP_STANCE),
  WIDE_GRIP(TechniqueCategory.GRIP_STANCE),
  SNATCH_GRIP(TechniqueCategory.GRIP_STANCE),
  HAMMER(TechniqueCategory.GRIP_STANCE),
  MIXED_GRIP(TechniqueCategory.GRIP_STANCE),
  HOOK_GRIP(TechniqueCategory.GRIP_STANCE),
  CONVENTIONAL(TechniqueCategory.GRIP_STANCE),
  SUMO(TechniqueCategory.GRIP_STANCE),

  // --- Starting Position ---
  HANG(TechniqueCategory.STARTING_POSITION),
  HIGH_HANG(TechniqueCategory.STARTING_POSITION),
  LOW_HANG(TechniqueCategory.STARTING_POSITION),
  FROM_BLOCKS(TechniqueCategory.STARTING_POSITION),
  DEFICIT(TechniqueCategory.STARTING_POSITION),
  TO_PLATFORM(TechniqueCategory.STARTING_POSITION),
  FEET_ELEVATED(TechniqueCategory.STARTING_POSITION),
  CHEST_TO_WALL(TechniqueCategory.STARTING_POSITION),
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
  STANDING(TechniqueCategory.BODY_POSITION),
  INCLINED(TechniqueCategory.BODY_POSITION),
  DECLINED(TechniqueCategory.BODY_POSITION),
  INVERTED(TechniqueCategory.BODY_POSITION),
  L_SIT(TechniqueCategory.BODY_POSITION),
  TUCK(TechniqueCategory.BODY_POSITION),
  PIKE(TechniqueCategory.BODY_POSITION),
  HOLLOW(TechniqueCategory.BODY_POSITION),
  ARCH(TechniqueCategory.BODY_POSITION),
  KNEES(TechniqueCategory.BODY_POSITION),
  HOLD(TechniqueCategory.BODY_POSITION),
  ROCK(TechniqueCategory.BODY_POSITION),

  // --- Laterality ---
  BILATERAL(TechniqueCategory.LATERALITY),
  UNILATERAL(TechniqueCategory.LATERALITY),
  ALTERNATING(TechniqueCategory.LATERALITY),
  ONE_ARM(TechniqueCategory.LATERALITY),
  SINGLE_LEG(TechniqueCategory.LATERALITY),
  DOUBLE(TechniqueCategory.LATERALITY),

  // --- Movement pattern / Direction ---
  FORWARD(TechniqueCategory.DIRECTION),
  BACKWARD(TechniqueCategory.DIRECTION),
  LATERAL(TechniqueCategory.DIRECTION),
  FACING(TechniqueCategory.DIRECTION),
  WALK(TechniqueCategory.DIRECTION),
  CROSSOVER(TechniqueCategory.DIRECTION),
  CROSS_BODY(TechniqueCategory.DIRECTION),
  CRAWL(TechniqueCategory.DIRECTION),
  BACKSTROKE(TechniqueCategory.DIRECTION),
  BREASTSTROKE(TechniqueCategory.DIRECTION),

  // --- Specific modifiers ---
  LEGLESS(TechniqueCategory.MODIFIER),
  WEIGHTED(TechniqueCategory.MODIFIER),
  BODYWEIGHT(TechniqueCategory.MODIFIER),
  ASSISTED(TechniqueCategory.MODIFIER),
  CONTINENTAL_CLEAN(TechniqueCategory.MODIFIER),
  RING(TechniqueCategory.MODIFIER),
  PIROUETTE(TechniqueCategory.MODIFIER),
  CLEAN_AND_PRESS(TechniqueCategory.MODIFIER),
  CLEAN_AND_JERK(TechniqueCategory.MODIFIER);

  private final TechniqueCategory category;

  /** Technique Category Inner Enum. */
  public enum TechniqueCategory {
    STYLE,
    GRIP_STANCE,
    STARTING_POSITION,
    LOAD_POSITION,
    BODY_POSITION,
    LATERALITY,
    DIRECTION,
    MODIFIER
  }
}
