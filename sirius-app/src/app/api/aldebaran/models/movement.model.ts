import {MuscleResponse, MuscleRole} from './muscle.model';

export type Modality =
  | 'WEIGHTLIFTING'
  | 'GYMNASTICS'
  | 'MONOSTRUCTURAL'
  | 'STRONGMAN';

export type Category =
// --- Weightlifting (WL) ---
  | 'DEADLIFT'
  | 'SQUAT'
  | 'PRESS_AND_JERK'
  | 'CLEAN'
  | 'SNATCH'
  | 'COMPLEXES'
  | 'LUNGES'
  | 'SWING'
  | 'OTHER_LIFTS'
  // --- Gymnastics (GY) ---
  | 'PULLING'
  | 'PUSHING'
  | 'INVERTED'
  | 'CORE'
  | 'LOCOMOTION_AND_BODY_CONTROL'
  // --- Monostructural (Metabolic Conditioning) ---
  | 'CARDIO'
  | 'CARDIO_MACHINES'
  | 'BURPEES'
  | 'JUMPING'
  // --- Strongman (Odd Objects) ---
  | 'THROWS_AND_SLAMS'
  | 'CARRY'
  | 'STRONGMAN_LIFTS'
  | 'SLED';

export type Equipment =
// --- Weightlifting & Strength ---
  | 'BARBELL'
  | 'PLATES'
  | 'DUMBBELL'
  | 'KETTLEBELL'
  | 'MEDICINE_BALL'
  | 'SANDBAG'
  | 'SLAM_BALL'
  // --- Gymnastics & Bodyweight Rig ---
  | 'PULL_UP_BAR'
  | 'RINGS'
  | 'PARALLETTES'
  | 'ROPE'
  | 'ABMAT'
  | 'BOX'
  // --- Monostructural / Cardio Machines ---
  | 'ROWER'
  | 'ASSAULT_BIKE'
  | 'ECHO_BIKE'
  | 'SKI_ERG'
  | 'BIKE_ERG'
  | 'GHD'
  | 'JUMP_ROPE'
  // --- Strongman & Odd Objects---
  | 'SLED'
  | 'YOKE'
  | 'BATTLE_ROPE'
  // --- None / Bodyweight ---
  | 'NONE';

export type Technique =
// --- Execution style & Tempo ---
  | 'STRICT'
  | 'KIPPING'
  | 'BUTTERFLY'
  | 'TEMPO'
  | 'PAUSED'
  | 'NEGATIVES'
  | 'DEAD_STOP'
  | 'HAND_RELEASE'
  | 'TOUCH_AND_GO'
  | 'EXPLOSIVE'
  // --- Grip & Stance ---
  | 'CLOSE_GRIP'
  | 'WIDE_GRIP'
  | 'SNATCH_GRIP'
  | 'HAMMER'
  | 'MIXED_GRIP'
  | 'HOOK_GRIP'
  | 'CONVENTIONAL'
  | 'SUMO'
  // --- Starting Position ---
  | 'HANG'
  | 'HIGH_HANG'
  | 'LOW_HANG'
  | 'FROM_BLOCKS'
  | 'DEFICIT'
  | 'TO_PLATFORM'
  | 'FEET_ELEVATED'
  | 'CHEST_TO_WALL'
  | 'BOX'
  // --- Load position ---
  | 'FRONT_RACK'
  | 'OVERHEAD'
  | 'BEHIND_THE_NECK'
  | 'BACK_RACK'
  | 'HIGH_BAR'
  | 'LOW_BAR'
  | 'BEAR_HUG'
  | 'ZERCHER'
  | 'ON_SHOULDER'
  | 'OVER_SHOULDER'
  | 'GOBLET'
  | 'FARMERS_CARRY'
  // ---  Body Position ---
  | 'SEATED'
  | 'STANDING'
  | 'INCLINED'
  | 'DECLINED'
  | 'INVERTED'
  | 'L_SIT'
  | 'TUCK_HOLD'
  | 'PIKE'
  | 'HOLLOW'
  | 'ARCH'
  | 'KNEES'
  // ---  Laterality ---
  | 'BILATERAL'
  | 'UNILATERAL'
  | 'ALTERNATING'
  | 'SINGLE_ARM'
  | 'SINGLE_LEG'
  // ---  Movement pattern / Direction ---
  | 'FORWARD'
  | 'BACKWARD'
  | 'LATERAL'
  | 'FACING'
  | 'WALK'
  | 'JUMPING'
  | 'CROSSOVER'
  | 'CRAWL_SWIM'
  | 'BACKSTROKE_SWIM'
  | 'BREASTSTROKE_SWIM'
  | 'BUTTERFLY_SWIM'
  // ---  Specific modifiers ---
  | 'LEGLESS'
  | 'WEIGHTED'
  | 'BODYWEIGHT'
  | 'ASSISTED'
  | 'CONTINENTAL_CLEAN';

/**
 * DTO for creating or updating a Movement in the catalog.
 * Mirrors `MovementRequest`.
 */
export interface MovementRequest {
  name: string;
  nameAbbreviation?: string;
  category: Category;
  equipment: Equipment[];
  techniques: Technique[];
  muscles?: MovementMuscleRequest[];
  involvesBodyweight: boolean;
  bodyweightFactor?: number;
  descriptionEn?: string;
  descriptionFr?: string;
  coachingCuesEn?: string;
  coachingCuesFr?: string;
  videoUrl?: string;
  imageUrl?: string;
}

/**
 * Full DTO for Movement display.
 * Mirrors `MovementResponse`.
 */
export interface MovementResponse {
  id: string;
  name: string;
  nameAbbreviation?: string;
  category: Category;
  equipment: Equipment[];
  techniques: Technique[];
  targetedMuscles: MovementMuscleResponse[];
  involvesBodyweight: boolean;
  bodyweightFactor: number;
  loadBearing: boolean;
  descriptionEn?: string;
  descriptionFr?: string;
  coachingCuesEn?: string;
  coachingCuesFr?: string;
  videoUrl?: string;
  imageUrl?: string;
}

/**
 * Lightweight DTO for listing movements.
 * Mirrors `MovementSummaryResponse`.
 */
export interface MovementSummaryResponse {
  id: string;
  name: string;
  nameAbbreviation?: string;
  category: Category;
  imageUrl?: string;
}

export interface MovementMuscleRequest {
  medicalName: string;
  role: MuscleRole;
  impactFactor?: number;
}

export interface MovementMuscleResponse {
  muscle: MuscleResponse;
  role: MuscleRole;
  impactFactor: number;
}
