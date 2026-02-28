import {Unit, WodSummaryResponse} from './wod.model';

export type ScalingLevel =
  | 'RX'
  | 'SCALED'
  | 'ELITE'
  | 'CUSTOM';

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
  weightUnit?: Unit;
  totalDistance?: number;
  distanceUnit?: Unit;
  totalCalories?: number;
  scaling: ScalingLevel;
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
  timeDisplayUnit?: Unit;
  rounds?: number;
  reps?: number;
  maxWeight?: number;
  totalLoad?: number;
  weightUnit?: Unit;
  totalDistance?: number;
  distanceUnit?: Unit;
  totalCalories?: number;
  scaling: ScalingLevel;
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
