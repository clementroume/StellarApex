import {Division} from '../../antares/models/user.model';
import {WodSummaryResponse} from './wod.model';

/** Level at which a WOD was performed; personal records rank ELITE (Rx+) > RX > SCALED > CUSTOM. */
export type ScalingLevel = 'RX' | 'SCALED' | 'ELITE' | 'CUSTOM';

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
  // --- Comments and Scaling notes---
  userComment?: string;
  scalingNotes?: string;
  // --- Gym where it was performed (public or personal WOD; a gym WOD uses its own gym) ---
  gymId?: number | null;
  // --- Session: the athlete's journal (optional for a session WOD, found or created) ---
  sessionLogId?: number | null;
  // --- Strength sets, set by set, in weightUnit (WEIGHT / LOAD / NONE scores) ---
  sets?: ScoreSet[];
  // --- Competition WOD: submit the score to the judges ---
  verificationRequested?: boolean;
}

/** One set of a strength score; a failed set is kept with completed = false. */
export interface ScoreSet {
  reps: number;
  weight?: number | null;
  completed: boolean;
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
  // --- Comments and Scaling notes---
  userComment?: string;
  scalingNotes?: string;
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
  sessionLogId?: number | null;
  sets?: ScoreSet[];
  // --- Per-user rights (computed per request) ---
  capabilities?: ScoreCapabilities;
}

export interface ScoreComparisonResponse {
  // --- Score Comparison Data ---
  rank: number;
  totalScores: number;
  percentile: number;
}

export interface ScoreReferenceData {
  // --- Scaling Level ---
  scalingLevels: string[];
}
