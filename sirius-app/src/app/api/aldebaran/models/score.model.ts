import {Division} from '../../antares/models/user.model';
import {WodMovementDisplay, WodSummaryResponse} from './wod.model';
import {MovementSummaryResponse} from './movement.model';
import {AgeGroup} from './record.model';

/**
 * Level chosen by the athlete (lot S, D140): RX = nothing changed, SCALED = changes detailed per line.
 * Personal records rank RX, then the WOD's official Scaled version, then any other Scaled.
 */
export type ScalingLevel = 'RX' | 'SCALED';

/** Rights of the current user on a score, computed by Aldebaran (ScoreSecurity). */
export interface ScoreCapabilities {
  // Update/delete: the athlete until the score is verified, the gym's SCORE_VERIFY staff, admins
  canModify: boolean;
  // Verify or withdraw the verification: SCORE_VERIFY staff of a competition WOD's gym, submitted scores only (not their own)
  canVerify: boolean;
}

export interface ScoreRequest {
  // --- Identification ---
  // Athlete, when competition staff log the score for them (defaults to the current user)
  userId?: number;
  date: string;
  wodId: number;
  // --- Time Metrics ---
  timeMinutes?: number;
  timeSeconds?: number;
  // --- Volume Metrics ---
  rounds?: number;
  reps?: number;
  // --- Load Metrics ---
  maxWeight?: number;
  totalLoad?: number;
  weightUnit?: string;
  // --- Distance Metrics ---
  totalDistance?: number;
  distanceUnit?: string;
  // --- Calories ---
  totalCalories?: number;
  // --- Performance Context ---
  scaling: ScalingLevel;
  timeCapped: boolean;
  /** Optional official tiebreak time (elapsed seconds), kept only for WODs that define one. */
  tiebreakSeconds?: number;
  // --- Comment ---
  userComment?: string;
  // --- Gym where it was performed (public or personal WOD; a gym WOD uses its own gym) ---
  gymId?: number | null;
  // --- Session: the athlete's journal (optional for a session WOD, found or created) ---
  sessionLogId?: number | null;
  // Block of the session the score is logged for (derived from the WOD when only one block holds it)
  sessionBlockId?: number | null;
  // --- Strength sets, set by set, in weightUnit, each on its line (WEIGHT / LOAD / NONE scores) ---
  sets?: ScoreSet[];
  // --- Lines done otherwise than prescribed (SCALED), or reps counted on "max" lines ---
  lines?: ScoreLineRequest[];
  // --- Competition WOD: submit the score to the judges ---
  verificationRequested?: boolean;
  // Team WOD: the other members of the team (same box), who get the same score
  teammateIds?: number[];
}

/** One set of a strength score; a failed set is kept with completed = false. */
export interface ScoreSet {
  reps: number;
  weight?: number | null;
  completed: boolean;
  // Line of the WOD (a complex: its first line); may be omitted when the WOD has one movement or complex
  wodMovementId?: number | null;
}

/**
 * A line done otherwise than prescribed (lot S, D142): the variant done and the quantities that changed (null =
 * as prescribed). The load is in the score's weight unit; on a "max" line, repsScheme holds the reps counted.
 */
export interface ScoreLineRequest {
  wodMovementId: number;
  movementId: number;
  equipment?: string[];
  techniques?: string[];
  repsScheme?: string | null;
  weight?: number | null;
  distance?: number | null;
  distanceUnit?: string | null;
  calories?: number | null;
  height?: number | null;
  heightUnit?: string | null;
  durationSeconds?: number | null;
}

/** A line done otherwise than prescribed, as stored (distance and height in meters, load in weightUnit). */
export interface ScoreLineResponse {
  wodMovementId: number;
  movement: MovementSummaryResponse;
  equipment: string[];
  techniques: string[];
  repsScheme?: string | null;
  weight?: number | null;
  distanceMeters?: number | null;
  calories?: number | null;
  heightMeters?: number | null;
  durationSeconds?: number | null;
  display?: WodMovementDisplay | null;
}

export interface ScoreResponse {
  // --- Identification ---
  id: number;
  userId: number;
  date: string;
  wodSummary: WodSummaryResponse;
  // Gym where the score was performed, if any
  gymId?: number | null;
  // --- Time Metrics ---
  timeSeconds?: number;
  timeMinutesPart?: number;
  timeSecondsPart?: number;
  timeDisplayUnit?: string;
  // --- Volume Metrics ---
  rounds?: number;
  reps?: number;
  // --- Load Metrics ---
  maxWeight?: number;
  totalLoad?: number;
  weightUnit?: string;
  // --- Distance Metrics ---
  totalDistance?: number;
  distanceUnit?: string;
  // --- Calories ---
  totalCalories?: number;
  // --- Performance Context ---
  scaling: ScalingLevel;
  timeCapped: boolean;
  tiebreakSeconds?: number;
  personalRecord: boolean;
  // Athlete's division when the score was logged (null: not chosen yet)
  division?: Division | null;
  // Athlete's age group in the year of the score (CrossFit Open brackets; null: no birth year)
  ageGroup?: AgeGroup | null;
  // Scaled score following the WOD's official Scaled version (ranked in Scaled)
  scaledOfficial?: boolean;
  // Rx+ badge: Scaled, every line at least RX and one harder (never ranked)
  rxPlus?: boolean;
  // --- Comment ---
  userComment?: string;
  // --- Audit ---
  loggedAt: string;
  // User who logged the score (the athlete, or staff logging it for them)
  loggedBy?: number;
  // --- Verification (competition WODs), absent while not verified ---
  verifiedBy?: number | null;
  verifiedAt?: string | null;
  // Submitted to the judges of a competition WOD
  verificationRequested?: boolean;
  // --- Session journal and strength sets (weights in weightUnit) ---
  // Team WOD: team of the score and its members (same performance)
  teamId?: number | null;
  teamMemberIds?: number[] | null;
  sessionLogId?: number | null;
  sets?: ScoreSet[];
  // Lines done otherwise than prescribed, or reps counted on "max" lines
  lines?: ScoreLineResponse[];
  // Reps of one round as done (scaled reps included), or null when the WOD has no fixed round
  roundReps?: number | null;
  // --- Per-user rights (computed per request) ---
  capabilities?: ScoreCapabilities;
}

/** How a score being written compares with the prescription (Aldebaran decides, nothing saved). */
export interface ScoreLevelResponse {
  asPrescribed: boolean;
  rxPlus: boolean;
  official: boolean;
}

export interface ScoreComparisonResponse {
  // --- Score Comparison Data ---
  rank: number;
  totalScores: number;
  percentile: number;
}

/** What one meter of running is worth on a cardio movement (D151), to propose a substitution's quantity. */
export interface CardioEquivalent {
  movementName: string;
  usualNameEn?: string | null;
  meters?: number | null;
  calories?: number | null;
  caloriesFemale?: number | null;
}

export interface ScoreReferenceData {
  scalingLevels: string[];
  cardio?: CardioEquivalent[];
}
