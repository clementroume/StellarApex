import {MovementResponse, MovementSummaryResponse} from './movement.model';

/** Part of a WOD a movement belongs to. */
export type WodSegment = 'BUY_IN' | 'MAIN' | 'BUY_OUT';

/** How the members of a team share the work, and how much a line asks from them. */
export type TeamMode = 'YOU_GO_I_GO' | 'SPLIT' | 'SYNCHRO';
export type TeamSplit = 'TOTAL' | 'EACH' | 'TOGETHER';

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
  // Sub-category of the format, and movement names in order (catalogue preview)
  structure?: string | null;
  // --- Team WOD: athletes per team (2 to 6) and how they share the work ---
  teamSize?: number | null;
  teamMode?: TeamMode | null;
  movementNames?: string[];
}

/** Values available to filter the WOD list, computed by Aldebaran with the list's security. */
export interface WodFacets {
  // WOD formats and categories present for the current filters (those two filters are ignored)
  wodTypes: string[];
  categories: string[];
  // Gyms owning readable WODs and authors of readable personal WODs (admin selectors)
  gymIds: number[];
  authorIds: number[];
  // Number of WODs per category (NONE = without category) and in all, for the search and scope
  categoryCounts?: Record<string, number>;
  total?: number;
  // Sub-categories present for the format asked
  structures?: string[];
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
  // --- Team WOD: athletes per team (2 to 6) and how they share the work ---
  teamSize?: number | null;
  teamMode?: TeamMode | null;
  // --- N of an "N RM" test (For Load WOD with one movement), or null ---
  repMax?: number | null;
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
  // --- Team WOD: athletes per team (2 to 6) and how they share the work ---
  teamSize?: number | null;
  teamMode?: TeamMode | null;
  // N of an "N RM" test, or null
  repMax?: number | null;
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
  // Length of the course (shuttle run: between the two lines, one rep = out and back), in lengthUnit
  length?: number;
  lengthUnit?: string;
  // --- Characteristics ---
  equipment?: string[];
  techniques?: string[];
  // --- Instructions (EN / FR) ---
  notesEn?: string;
  notesFr?: string;
  // --- Prescribed number of sets (strength / skill work) ---
  sets?: number | null;
  // --- Part of the WOD: before the rounds, the rounds, after them (null = MAIN) ---
  segment?: WodSegment | null;
  // --- Group of a weightlifting complex (lines done as one set), or null ---
  complexGroup?: number | null;
  // --- Team WOD: quantity in total (default), by each member, or together ---
  teamSplit?: TeamSplit | null;
  // --- Load as a percentage of the athlete's rmReps RM (base version of the movement) ---
  percentOfRm?: number | null;
  rmReps?: number | null;
  // --- Techniques of one component of a chained line ---
  componentTechniques?: ComponentTechnique[];
  // --- This line in the WOD's official Scaled version, or null (kept RX) ---
  scaled?: WodScaledLineRequest | null;
}

/**
 * A line in the WOD's official Scaled version (lot S, D141): the variant and the quantities in that version, a
 * value left null being the RX one; units of origin, like the RX line.
 */
export interface WodScaledLineRequest {
  movementId: number;
  equipment?: string[];
  techniques?: string[];
  repsScheme?: string | null;
  weight?: number | null;
  weightFemale?: number | null;
  weightUnit?: string | null;
  distance?: number | null;
  distanceUnit?: string | null;
  calories?: number | null;
  caloriesFemale?: number | null;
  height?: number | null;
  heightFemale?: number | null;
  heightUnit?: string | null;
  durationSeconds?: number | null;
}

/** A line in the official Scaled version, every value filled in (the RX one where the version keeps it). */
export interface WodScaledLineResponse {
  movement: MovementSummaryResponse;
  equipment: string[];
  techniques: string[];
  repsScheme?: string | null;
  weight?: number | null;
  weightFemale?: number | null;
  weightUnit?: string | null;
  distance?: number | null;
  distanceUnit?: string | null;
  calories?: number | null;
  caloriesFemale?: number | null;
  height?: number | null;
  heightFemale?: number | null;
  heightUnit?: string | null;
  durationSeconds?: number | null;
  display?: WodMovementDisplay | null;
  equivalents?: LineEquivalents | null;
}

/**
 * A line's load and height in both systems (D149, D155): the prescription in its own unit, and in the other unit
 * the value boxes use (real implement sizes; exact in competitions). Shown as is, never converted by Sirius.
 */
export interface LineEquivalents {
  weightKg?: number | null;
  weightFemaleKg?: number | null;
  weightLb?: number | null;
  weightFemaleLb?: number | null;
  heightCm?: number | null;
  heightFemaleCm?: number | null;
  heightIn?: number | null;
  heightFemaleIn?: number | null;
}

/** Real sizes of the weighted implements and plyo boxes in both systems (D155). */
export interface ImplementSizes {
  kettlebellKg: number[];
  kettlebellLb: number[];
  dumbbellKg: number[];
  dumbbellLb: number[];
  medballKg: number[];
  medballLb: number[];
  boxCm: number[];
  boxIn: number[];
}

/** A technique of one component of a chained line (Power Clean and Split Jerk: 1 POWER, 2 SPLIT). */
export interface ComponentTechnique {
  componentOrder: number;
  technique: string;
}

/** How a line reads, decided by the catalog (WodMovementDisplay). */
export interface WodMovementDisplay {
  usualNameEn?: string | null;
  usualNameFr?: string | null;
  remainingEquipment: string[];
  remainingTechniques: string[];
  implementCount?: number | null;
  variant: boolean;
  percentBaseKey: string;
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
  // Length of the course (shuttle run: between the two lines, one rep = out and back), in lengthUnit
  length?: number;
  lengthUnit?: string;
  // --- Characteristics ---
  equipment?: string[];
  techniques?: string[];
  // --- Instructions (EN / FR) ---
  notesEn?: string;
  notesFr?: string;
  // --- Prescribed number of sets (strength / skill work) ---
  sets?: number | null;
  // --- Part of the WOD: before the rounds, the rounds, after them (null = MAIN) ---
  segment?: WodSegment | null;
  // --- Group of a weightlifting complex (lines done as one set), or null ---
  complexGroup?: number | null;
  // --- Team WOD: quantity in total (default), by each member, or together ---
  teamSplit?: TeamSplit | null;
  // --- Load as a percentage of the athlete's rmReps RM (base version of the movement) ---
  percentOfRm?: number | null;
  rmReps?: number | null;
  componentTechniques?: ComponentTechnique[];
  display?: WodMovementDisplay | null;
  // --- Load and height in both systems, as boxes load them (D149) ---
  equivalents?: LineEquivalents | null;
  // --- This line in the WOD's official Scaled version, or null (kept RX) ---
  scaled?: WodScaledLineResponse | null;
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
  // --- Real sizes of the weighted implements (D155) ---
  implementSizes?: ImplementSizes;
}


