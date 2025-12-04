package apex.stellar.aldebaran.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Defines specific variations, styles, or modifications applied to a standard movement.
 *
 * <p>A "Technique" alters <em>how</em> a movement is performed without changing its fundamental
 * nature. Examples:
 *
 * <ul>
 *   <li>"Strict" vs "Kipping" (Execution Style)
 *   <li>"Hang" vs "From Blocks" (Starting Position)
 *   <li>"Single Arm" vs "Bilateral" (Unilateralism)
 * </ul>
 *
 * <p>This allows for a compact exercise database (e.g., one "Snatch" entity with multiple technique
 * tags).
 */
@Getter
@RequiredArgsConstructor
public enum Technique {

  // --- Execution style & Tempo ---
  STRICT("Strict", TechniqueCategory.STYLE),
  KIPPING("Kipping", TechniqueCategory.STYLE),
  BUTTERFLY("Butterfly", TechniqueCategory.STYLE),
  TEMPO("Tempo", TechniqueCategory.STYLE),
  PAUSED("Paused", TechniqueCategory.STYLE),
  NEGATIVES("Negatives (Eccentric)", TechniqueCategory.STYLE),
  DEAD_STOP("Dead Stop", TechniqueCategory.STYLE),
  HAND_RELEASE("Hand Release", TechniqueCategory.STYLE),
  TOUCH_AND_GO("Touch & Go", TechniqueCategory.STYLE),
  EXPLOSIVE("Explosive", TechniqueCategory.STYLE),

  // --- Grip & Stance ---
  CLOSE_GRIP("Close Grip", TechniqueCategory.GRIP_STANCE),
  WIDE_GRIP("Wide Grip", TechniqueCategory.GRIP_STANCE),
  SNATCH_GRIP("Snatch Grip", TechniqueCategory.GRIP_STANCE),
  HAMMER("Hammer Grip", TechniqueCategory.GRIP_STANCE),
  MIXED_GRIP("Mixed Grip", TechniqueCategory.GRIP_STANCE),
  HOOK_GRIP("Hook Grip", TechniqueCategory.GRIP_STANCE),
  CONVENTIONAL("Conventional", TechniqueCategory.GRIP_STANCE),
  SUMO("Sumo", TechniqueCategory.GRIP_STANCE),

  // --- Starting Position ---
  HANG("Hang", TechniqueCategory.STARTING_POSITION),
  HIGH_HANG("High Hang", TechniqueCategory.STARTING_POSITION),
  LOW_HANG("Low Hang / Below Knee", TechniqueCategory.STARTING_POSITION),
  FROM_BLOCKS("From Blocks", TechniqueCategory.STARTING_POSITION),
  DEFICIT("Deficit", TechniqueCategory.STARTING_POSITION),
  TO_PLATFORM("To Platform", TechniqueCategory.STARTING_POSITION),
  FEET_ELEVATED("Feet Elevated", TechniqueCategory.STARTING_POSITION),
  CHEST_TO_WALL("Chest to Wall", TechniqueCategory.STARTING_POSITION),
  BOX("Box", TechniqueCategory.STARTING_POSITION),

  // --- Load position ---
  FRONT_RACK("Front Rack", TechniqueCategory.LOAD_POSITION),
  OVERHEAD("Overhead", TechniqueCategory.LOAD_POSITION),
  BEHIND_THE_NECK("Behind the Neck", TechniqueCategory.LOAD_POSITION),
  BACK_RACK("Back Rack", TechniqueCategory.LOAD_POSITION),
  HIGH_BAR("High Bar", TechniqueCategory.LOAD_POSITION),
  LOW_BAR("Low Bar", TechniqueCategory.LOAD_POSITION),
  BEAR_HUG("Bear Hug", TechniqueCategory.LOAD_POSITION),
  ZERCHER("Zercher", TechniqueCategory.LOAD_POSITION),
  ON_SHOULDER("On Shoulder", TechniqueCategory.LOAD_POSITION),
  OVER_SHOULDER("Over Shoulder", TechniqueCategory.LOAD_POSITION),
  GOBLET("Goblet", TechniqueCategory.LOAD_POSITION),
  FARMERS_CARRY("Farmer's Carry", TechniqueCategory.LOAD_POSITION),

  // ---  Body Position ---
  SEATED("Seated", TechniqueCategory.BODY_POSITION),
  STANDING("Standing", TechniqueCategory.BODY_POSITION),
  INCLINED("Inclined", TechniqueCategory.BODY_POSITION),
  DECLINED("Declined", TechniqueCategory.BODY_POSITION),
  INVERTED("Inverted", TechniqueCategory.BODY_POSITION),
  L_SIT("L-Sit", TechniqueCategory.BODY_POSITION),
  TUCK_HOLD("Tuck", TechniqueCategory.BODY_POSITION),
  PIKE("Pike", TechniqueCategory.BODY_POSITION),
  HOLLOW("Hollow", TechniqueCategory.BODY_POSITION),
  ARCH("Arch", TechniqueCategory.BODY_POSITION),
  KNEES("Kneeling", TechniqueCategory.BODY_POSITION),

  // ---  Laterality ---
  BILATERAL("Bilateral", TechniqueCategory.LATERALITY),
  UNILATERAL("Unilateral", TechniqueCategory.LATERALITY),
  ALTERNATING("Alternating", TechniqueCategory.LATERALITY),
  SINGLE_ARM("Single Arm", TechniqueCategory.LATERALITY),
  SINGLE_LEG("Single Leg", TechniqueCategory.LATERALITY),

  // ---  Movement pattern / Direction ---
  FORWARD("Forward", TechniqueCategory.DIRECTION),
  BACKWARD("Backward", TechniqueCategory.DIRECTION),
  LATERAL("Lateral", TechniqueCategory.DIRECTION),
  FACING("Facing", TechniqueCategory.DIRECTION),
  WALK("Walk", TechniqueCategory.DIRECTION),
  JUMPING("Jumping", TechniqueCategory.DIRECTION),
  CROSSOVER("Crossover", TechniqueCategory.DIRECTION),
  CRAWL_SWIM("Crawl Swim", TechniqueCategory.DIRECTION),
  BACKSTROKE_SWIM("Backstroke Swim", TechniqueCategory.DIRECTION),
  BREASTSTROKE_SWIM("Breaststroke Swim", TechniqueCategory.DIRECTION),
  BUTTERFLY_SWIM("Butterfly Swim", TechniqueCategory.DIRECTION),

  // ---  Specific modifiers ---
  LEGLESS("Legless", TechniqueCategory.MODIFIER),
  WEIGHTED("Weighted", TechniqueCategory.MODIFIER),
  BODYWEIGHT("Bodyweight", TechniqueCategory.MODIFIER),
  ASSISTED("Assisted", TechniqueCategory.MODIFIER),
  CONTINENTAL_CLEAN("Continental", TechniqueCategory.MODIFIER);

  /** A human-readable name suitable for UI display. */
  private final String displayName;

  /** The category of the technique variation. */
  private final TechniqueCategory category;

  /** Categorizes techniques by their primary effect on the movement. */
  public enum TechniqueCategory {
    /** Changes the tempo or dynamic quality of the movement (e.g., Strict vs Kipping). */
    STYLE,
    /** Changes how the implement is gripped or the foot stance. */
    GRIP_STANCE,
    /** Changes the starting height or range of motion. */
    STARTING_POSITION,
    /** Changes where the load is held on the body. */
    LOAD_POSITION,
    /** Changes the athlete's body orientation. */
    BODY_POSITION,
    /** Defines limb usage (One arm, Alternating). */
    LATERALITY,
    /** Defines movement direction or locomotion type. */
    DIRECTION,
    /** Specific constraints or equipment-driven variations. */
    MODIFIER
  }
}
