import {WodSummaryResponse} from './wod.model';

/**
 * DTO for logging a new workout performance.
 * Mirrors `WodScoreRequest`.
 */
export interface WodScoreRequest {
  userId?: number;
  wodId: number;
  date: string;
  timeMinutes?: number;
  timeSeconds?: number;
  rounds?: number;
  reps?: number;
  maxWeight?: number;
  totalLoad?: number;
  weightUnit?: string;
  totalDistance?: number;
  distanceUnit?: string;
  totalCalories?: number;
  scaling: string;
  timeCapped: boolean;
  scalingNotes?: string;
  userComment?: string;
}

/**
 * DTO representing a logged score.
 * Mirrors `WodScoreResponse`.
 */
export interface WodScoreResponse {
  id: number;
  userId: number;
  date: string;
  wodSummary: WodSummaryResponse;
  timeSeconds?: number;
  timeMinutesPart?: number;
  timeSecondsPart?: number;
  timeDisplayUnit?: string;
  rounds?: number;
  reps?: number;
  maxWeight?: number;
  totalLoad?: number;
  weightUnit?: string;
  totalDistance?: number;
  distanceUnit?: string;
  totalCalories?: number;
  scaling: string;
  personalRecord: boolean;
  timeCapped: boolean;
  userComment?: string;
  scalingNotes?: string;
}

export interface ScoreComparisonResponse {
  rank: number;
  totalScores: number;
  percentile: number;
}

export interface WodScoreReferenceData {
  scalingLevels: string[];
}
