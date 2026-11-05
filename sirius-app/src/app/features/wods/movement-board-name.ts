/**
 * Name of a WOD movement as written on a box whiteboard, in CrossFit English jargon whatever the
 * language shown: its variants built into the name ("Hang Power Clean", "Bar Muscle-Up", "Single-Arm
 * Dumbbell Snatch") and the plural for a number of reps ("9 Hang Power Cleans").
 */

// Words of the techniques written before the equipment: limbs, then style ("Single-Arm Strict ...")
const LIMB_WORDS: Record<string, string> = {
  ONE_ARM: 'Single-Arm', UNILATERAL: 'Single-Arm', SINGLE_LEG: 'Single-Leg', ALTERNATING: 'Alternating',
  DOUBLE: 'Double', BILATERAL: ''
};
// Words of the techniques written between the equipment and the name ("Dumbbell Hang Clean")
const POSITION_WORDS: Record<string, string> = {
  HANG: 'Hang', HIGH_HANG: 'High-Hang', LOW_HANG: 'Low-Hang', FROM_BLOCKS: 'Block', FRONT_RACK: 'Front-Rack',
  OVERHEAD: 'Overhead', BACK_RACK: 'Back-Rack', BEHIND_THE_NECK: 'Behind-the-Neck', HIGH_BAR: 'High-Bar',
  LOW_BAR: 'Low-Bar', BEAR_HUG: 'Bear Hug', ZERCHER: 'Zercher', ON_SHOULDER: 'Shouldered',
  OVER_SHOULDER: 'Over-the-Shoulder', GOBLET: 'Goblet', FARMERS_CARRY: "Farmer's", CONVENTIONAL: '', SUMO: 'Sumo',
  CLOSE_GRIP: 'Close-Grip', WIDE_GRIP: 'Wide-Grip', SNATCH_GRIP: 'Snatch-Grip', HAMMER: 'Hammer',
  MIXED_GRIP: 'Mixed-Grip', HOOK_GRIP: 'Hook-Grip', WALK: 'Walking', CONTINENTAL_CLEAN: 'Continental',
  CLEAN_AND_PRESS: 'Clean & Press'
};
const STYLE_WORDS: Record<string, string> = {
  STRICT: 'Strict', KIPPING: 'Kipping', BUTTERFLY: 'Butterfly', TEMPO: 'Tempo', PAUSED: 'Paused',
  NEGATIVES: 'Negative', DEAD_STOP: 'Dead-Stop', HAND_RELEASE: 'Hand-Release', TOUCH_AND_GO: 'Touch-and-Go',
  EXPLOSIVE: 'Explosive', JUMPING: 'Jumping', DEFICIT: 'Deficit', TO_PLATFORM: 'Platform',
  FEET_ELEVATED: 'Feet-Elevated', CHEST_TO_WALL: 'Chest-to-Wall', FREESTANDING: 'Freestanding', BOX: 'Box',
  SEATED: 'Seated', STANDING: 'Standing', INCLINED: 'Incline', DECLINED: 'Decline', INVERTED: 'Inverted',
  L_SIT: 'L-Sit', TUCK: 'Tuck', PIKE: 'Pike', HOLLOW: 'Hollow', ARCH: 'Arch', KNEES: 'Kneeling',
  ROCK: 'Rocking', FORWARD: '', BACKWARD: 'Backward', LATERAL: 'Lateral', FACING: 'Facing',
  CROSSOVER: 'Crossover', CROSS_BODY: 'Cross-Body', CRAWL: 'Freestyle', BACKSTROKE: 'Backstroke',
  BREASTSTROKE: 'Breaststroke', LEGLESS: 'Legless', WEIGHTED: 'Weighted', BODYWEIGHT: 'Bodyweight',
  ASSISTED: 'Assisted', RING: 'Ring', PIROUETTE: 'Pirouette', HOLD: ''
};
const EQUIPMENT_WORDS: Record<string, string> = {
  BARBELL: 'Barbell', PLATES: 'Plate', DUMBBELL: 'Dumbbell', KETTLEBELL: 'Kettlebell', TRAP_BAR: 'Trap Bar',
  AXLE_BAR: 'Axle', MEDICINE_BALL: 'Med Ball', D_BALL: 'D-Ball', SANDBAG: 'Sandbag', SLAM_BALL: 'Slam Ball',
  WEIGHT: 'Weighted', BENCH: 'Bench', RINGS: 'Ring', PARALLETTES: 'Parallette', PARALLEL_BARS: 'Parallel Bar',
  MATADOR: 'Matador', ROPE: 'Rope', PEGBOARD: 'Pegboard', ABMAT: 'AbMat', BOX: 'Box', ELASTIC_BAND: 'Banded',
  GHD: 'GHD', SLED: 'Sled', SLED_AND_ROPE: 'Sled', HARNESS: 'Harness', YOKE: 'Yoke', BATTLE_ROPE: 'Battle Rope',
  HANDLES: "Farmer's Handle", TIRE: 'Tire', ATLAS_STONE: 'Atlas Stone', KEG: 'Keg', LOG: 'Log',
  // The pull-up bar only tells a bar muscle-up from a ring one; elsewhere it is implied
  PULL_UP_BAR: '', JUMP_ROPE: ''
};
// Machines and monostructural movements: the board word ("Echo Bike", "Row", "Run")
const MACHINE_WORDS: Record<string, string> = {
  ROWER: 'Row', ASSAULT_BIKE: 'Assault Bike', ECHO_BIKE: 'Echo Bike', BIKE_ERG: 'BikeErg', SKI_ERG: 'Ski',
  TREADMILL: 'Treadmill Run', CURVED_TREADMILL: 'Curved Treadmill Run'
};
const ALIASES: Record<string, string> = {Running: 'Run', Rowing: 'Row', Biking: 'Bike', 'Ski Erg': 'Ski', Swimming: 'Swim'};
// Small words kept in lower case inside a hyphenated name ("Toes-to-Bar")
const SMALL_WORDS = ['to', 'and', 'the', 'of'];

/** A variant as written on the board, or the movement's own word. */
export interface BoardMovement {
  name: string;
  /** Equipment the catalogue movement allows: its only one is implied ("Wall-Ball Shot", not "Med Ball ..."). */
  allowed?: string[] | null;
  equipment?: string[] | null;
  techniques?: string[] | null;
}

/**
 * Board name of a movement in a WOD, plural when `plural` (a number of reps other than one).
 * Equipment already in the catalogue name ("Box Step-up", "Rope Climb") is not repeated.
 */
export function boardName(movement: BoardMovement, plural = false): string {
  const equipment = movement.equipment ?? [];
  const techniques = movement.techniques ?? [];
  const special = specialName(movement.name, equipment, techniques);
  if (special) return plural ? special.plural : special.singular;

  const machine = equipment.map(eq => MACHINE_WORDS[eq]).find(Boolean);
  const base = capitalizeHyphens(machine ?? ALIASES[movement.name] ?? movement.name);
  const inName = (word: string) => base.toLowerCase().includes(word.toLowerCase());
  const words = (map: Record<string, string>, values: string[]) =>
    values.filter(v => v in map).map(v => map[v]).filter(w => w && !inName(w));

  const limbs = words(LIMB_WORDS, techniques);
  const styles = words(STYLE_WORDS, techniques);
  const implied = movement.allowed?.length === 1 ? movement.allowed[0] : null;
  const gear = machine ? [] : words(EQUIPMENT_WORDS, equipment.filter(eq => eq !== implied));
  const positions = words(POSITION_WORDS, techniques);
  // "Bar Muscle-Up", "Burpee Bar Muscle-Up": the bar or the rings sit right before "Muscle-Up"
  let name = base;
  if (/muscle-up/i.test(base)) {
    const implement = equipment.includes('PULL_UP_BAR') ? 'Bar' : equipment.includes('RINGS') ? 'Ring' : '';
    if (implement) name = base.replace(/muscle-up/i, `${implement} Muscle-Up`);
    gear.splice(0, gear.length, ...gear.filter(w => w !== 'Ring'));
  }
  // "Toes-to-Rings"
  if (/toes-to-bar/i.test(base) && equipment.includes('RINGS')) {
    name = base.replace(/toes-to-bar/i, 'Toes-to-Rings');
    gear.splice(0, gear.length, ...gear.filter(w => w !== 'Ring'));
  }
  const pluralName = plural ? pluralize(name) : name;
  const held = techniques.includes('HOLD') && !/hold\b/i.test(pluralName) ? ' Hold' : '';
  return [...unique([...limbs, ...styles, ...gear, ...positions]), pluralName].join(' ') + held;
}

/** Plural of the last word of a name: "Snatches", "Muscle-Ups", "Carries", "Knees-to-Elbows" unchanged. */
export function pluralize(name: string): string {
  const match = /^(.*?)([A-Za-z']+)$/.exec(name);
  if (!match) return name;
  const [, head, word] = match;
  if (/(ss|ch|sh|x|z)$/i.test(word)) return `${head}${word}es`;
  if (/s$/i.test(word)) return name;
  if (/[^aeiou]y$/i.test(word)) return `${head}${word.slice(0, -1)}ies`;
  return `${head}${word}s`;
}

/** Hyphenated words start with a capital ("Muscle-Up", "Sit-Up"), small words stay ("Toes-to-Bar"). */
function capitalizeHyphens(name: string): string {
  return name.replace(/-([a-z]+)/g, (all, word: string) =>
    SMALL_WORDS.includes(word) ? all : `-${word[0].toUpperCase()}${word.slice(1)}`);
}

/** Names the generic order cannot say: burpees over an implement. */
function specialName(name: string, equipment: string[], techniques: string[]): {singular: string; plural: string} | null {
  if (name !== 'Burpee Over') return null;
  const lateral = techniques.includes('LATERAL') ? 'Lateral ' : '';
  if (equipment.includes('BARBELL') && techniques.includes('FACING')) {
    return {singular: `${lateral}Bar-Facing Burpee`, plural: `${lateral}Bar-Facing Burpees`};
  }
  const implement = equipment.includes('BARBELL') ? 'the Bar'
    : equipment.includes('DUMBBELL') ? 'the Dumbbell'
      : equipment.includes('BOX') ? 'the Box' : '';
  const over = implement ? ` Over ${implement}` : ' Over';
  return {singular: `${lateral}Burpee${over}`, plural: `${lateral}Burpees${over}`};
}

function unique(words: string[]): string[] {
  return words.filter((w, i) => words.indexOf(w) === i);
}
