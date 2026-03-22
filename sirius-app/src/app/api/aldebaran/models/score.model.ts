import {WodSummaryResponse} from './wod.model';

export interface ScoreRequest {
  // --- Identification ---
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
  scaling: string;
  timeCapped: boolean;
  // --- Comments and Scaling notes---
  userComment?: string;
  scalingNotes?: string;
}

export interface ScoreResponse {
  // --- Identification ---
  id: number;
  userId: number;
  date: string;
  wodSummary: WodSummaryResponse;
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
  scaling: string;
  timeCapped: boolean;
  personalRecord: boolean;
  // --- Comments and Scaling notes---
  userComment?: string;
  scalingNotes?: string;
  // --- Audit ---
  loggedAt: string;
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
