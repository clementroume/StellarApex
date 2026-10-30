import {MovementResponse} from './movement.model';

/** Part of a WOD a movement belongs to. */
export type WodSegment = 'BUY_IN' | 'MAIN' | 'BUY_OUT';

export type WodScope = 'ALL' | 'MINE' | 'GYM' | 'PUBLIC';

export interface WodSummaryResponse {
  id: number;
  title: string;
  // Format (FOR_TIME, AMRAP, EMOM...) and optional category (GIRLS, HERO, OPEN...)
  wodType: string;
  category?: string | null;
  scoreType: string;
  repScheme?: string;
  timeCapSeconds?: number;
  /** Reps of one round of an AMRAP with a fixed round; scores are total reps shown as rounds + reps. */
  repsPerRound?: number;
  authorId?: number;
  gymId?: number;
  isPublic: boolean;
  // Gym competition WOD: its staff logs and verifies the scores
  competition: boolean;
  createdAt: string;
}

/** Values available to filter the WOD list, computed by Aldebaran with the list's security. */
export interface WodFacets {
  // WOD formats and categories present for the current filters (those two filters are ignored)
  wodTypes: string[];
  // Sub-categories of each format
  structures: Record<string, string[]>;
  categories: string[];
  // Gyms owning readable WODs and authors of readable personal WODs (admin selectors)
  gymIds: number[];
  authorIds: number[];
}

/** Rights of the current user on a WOD, computed by Aldebaran (WodSecurity). */
export interface WodCapabilities {
  canModify: boolean;
  // The WOD has logged scores: edition and deletion are refused
  locked: boolean;
  // Competition WOD judged by the user (SCORE_VERIFY in its gym): log scores for its members, verify scores
  canJudge: boolean;
}

export interface WodRequest {
  // --- Identification ---
  title: string;
  wodType: string;
  category?: string | null;
  scoreType: string;
  // --- Authorship and Visibility ---
  authorId?: number;
  gymId?: number;
  isPublic: boolean;
  // Competition WOD: gym WOD only, never public (Aldebaran rejects it otherwise)
  competition: boolean;
  // --- Description and Notes (EN / FR, either may be empty) ---
  descriptionEn?: string;
  descriptionFr?: string;
  notesEn?: string;
  notesFr?: string;
  // --- Official tiebreak (EN / FR), only for WODs that define one ---
  tiebreakEn?: string;
  tiebreakFr?: string;
  // --- Structure and Prescription ---
  timeCapSeconds?: number;
  /** Reps of one round of an AMRAP with a fixed round; scores are total reps shown as rounds + reps. */
  repsPerRound?: number;
  emomInterval?: number;
  emomRounds?: number;
  repScheme?: string;
  // --- Structure of the quick editor ---
  // Rounds of a "rounds for time", rest between rounds / intervals and work time of an interval (s)
  rounds?: number | null;
  restSeconds?: number | null;
  workSeconds?: number | null;
  // Ascending ladder (3-6-9...): first rung and step
  ladderStart?: number | null;
  ladderStep?: number | null;
  // Weight vest worn for the whole WOD (men's, women's), in vestWeightUnit
  vestWeight?: number | null;
  vestWeightFemale?: number | null;
  vestWeightUnit?: string | null;
  // --- Sub-category of the format (For Time: ROUNDS, SCHEME, CHIPPER...) ---
  structure?: string | null;
  movements: WodMovementRequest[];
}

export interface WodResponse {
  // --- Identification ---
  id: number;
  title: string;
  wodType: string;
  category?: string | null;
  scoreType: string;
  // --- Authorship and Visibility ---
  authorId?: number;
  gymId?: number;
  isPublic: boolean;
  competition: boolean;
  // WOD this one was copied from (e.g. a public Open WOD copied for a gym competition)
  sourceWodId?: number | null;
  // False for the WOD of a session block: read and edited through its session
  standalone?: boolean;
  // --- Description and Notes (EN / FR, either may be empty) ---
  descriptionEn?: string;
  descriptionFr?: string;
  notesEn?: string;
  notesFr?: string;
  // --- Official tiebreak (EN / FR), only for WODs that define one ---
  tiebreakEn?: string;
  tiebreakFr?: string;
  // --- Structure and Prescription ---
  timeCapSeconds?: number;
  /** Reps of one round of an AMRAP with a fixed round; scores are total reps shown as rounds + reps. */
  repsPerRound?: number;
  emomInterval?: number;
  emomRounds?: number;
  repScheme?: string;
  // --- Structure of the quick editor ---
  // Rounds of a "rounds for time", rest between rounds / intervals and work time of an interval (s)
  rounds?: number | null;
  restSeconds?: number | null;
  workSeconds?: number | null;
  // Ascending ladder (3-6-9...): first rung and step
  ladderStart?: number | null;
  ladderStep?: number | null;
  // Weight vest worn for the whole WOD (men's, women's), in vestWeightUnit
  vestWeight?: number | null;
  vestWeightFemale?: number | null;
  vestWeightUnit?: string | null;
  // --- Sub-category of the format (For Time: ROUNDS, SCHEME, CHIPPER...) ---
  structure?: string | null;
  modalities: string[];
  movements: WodMovementResponse[];
  // --- Rights ---
  capabilities?: WodCapabilities;
  // --- Audit ---
  createdAt: string;
  updatedAt: string;
}

/** Copy of a readable WOD into a gym (WOD_WRITE there) or as a personal WOD (gymId null). */
export interface WodCopyRequest {
  gymId: number | null;
  // Competition copy (gym copies only)
  competition: boolean;
}

export interface WodMovementRequest {
  // ---  Identification ---
  movementId: number;
  orderIndex: number;
  // --- Prescription ---
  repsScheme?: string;
  // Men's load, women's load in the same unit
  weight?: number;
  weightFemale?: number;
  weightUnit?: string;
  durationSeconds?: number;
  durationDisplayUnit?: string;
  distance?: number;
  distanceUnit?: string;
  calories?: number;
  caloriesFemale?: number;
  // Box or target height (men's, women's), in heightUnit
  height?: number;
  heightFemale?: number;
  heightUnit?: string;
  // --- Characteristics ---
  equipment?: string[];
  techniques?: string[];
  // --- Instructions (EN / FR) ---
  notesEn?: string;
  notesFr?: string;
  scalingOptionsEn?: string;
  scalingOptionsFr?: string;
  // --- Prescribed number of sets (strength / skill work) ---
  sets?: number | null;
  // --- Part of the WOD: before the rounds, the rounds, after them (null = MAIN) ---
  segment?: WodSegment | null;
  // --- Group of a weightlifting complex (lines done as one set), or null ---
  complexGroup?: number | null;
}

export interface WodMovementResponse {
  // --- Identification ---
  id: number;
  movement: MovementResponse;
  orderIndex: number;
  // --- Prescription ---
  repsScheme?: string;
  // Men's load, women's load in the same unit
  weight?: number;
  weightFemale?: number;
  weightUnit?: string;
  durationSeconds?: number;
  durationDisplayUnit?: string;
  distance?: number;
  distanceUnit?: string;
  calories?: number;
  caloriesFemale?: number;
  // Box or target height (men's, women's), in heightUnit
  height?: number;
  heightFemale?: number;
  heightUnit?: string;
  // --- Characteristics ---
  equipment?: string[];
  techniques?: string[];
  // --- Instructions (EN / FR) ---
  notesEn?: string;
  notesFr?: string;
  scalingOptionsEn?: string;
  scalingOptionsFr?: string;
  // --- Prescribed number of sets (strength / skill work) ---
  sets?: number | null;
  // --- Part of the WOD: before the rounds, the rounds, after them (null = MAIN) ---
  segment?: WodSegment | null;
  // --- Group of a weightlifting complex (lines done as one set), or null ---
  complexGroup?: number | null;
}

export interface WodReferenceData {
  // --- Wod Formats and Categories ---
  wodTypes: string[];
  // Sub-categories of each format
  structures: Record<string, string[]>;
  categories: string[];
  // --- Score Types ---
  scoreTypes: string[];
  // --- Units ---
  unitGroups: Record<string, string[]>;
}


