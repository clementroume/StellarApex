import {WodSummaryResponse} from './wod.model';

/** Level at which a WOD was performed; personal records rank ELITE (Rx+) > RX > SCALED > CUSTOM. */
export type ScalingLevel = 'RX' | 'SCALED' | 'ELITE' | 'CUSTOM';

/** Rights of the current user on a score, computed by Aldebaran (ScoreSecurity). */
export interface ScoreCapabilities {
  // Update/delete: the athlete until the score is verified, the gym's SCORE_VERIFY staff, admins
  canModify: boolean;
  // Verify or withdraw the verification: SCORE_VERIFY staff of a competition WOD's gym (not on their own score)
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
