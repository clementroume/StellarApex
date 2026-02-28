import {Modality, MovementResponse} from './movement.model';

export type WodType =
// --- Time-based ---
  | 'AMRAP'
  | 'EMOM'
  | 'FOR_TIME'
  | 'TABATA'
  // --- Training Focus ---
  | 'STRENGTH'
  | 'SKILL'
  | 'ACCESSORY'
  // --- Special Formats ---
  | 'CHIPPER'
// --- Benchmarks ---
  | 'GIRLS'
  | 'HERO'
  | 'CUSTOM_BENCHMARK'
// --- Other ---
  | 'CUSTOM';
export type ScoreType =
  | 'TIME'
  | 'ROUNDS_REPS'
  | 'REPS'
  | 'WEIGHT'
  | 'LOAD'
  | 'CALORIES'
  | 'DISTANCE'
  | 'NONE';
export type Unit =
  | 'KG'
  | 'LBS'
  | 'SECONDS'
  | 'MINUTES'
  | 'METERS'
  | 'KILOMETERS'
  | 'FEET'
  | 'YARDS'
  | 'MILES';

/**
 * DTO for creating or updating a WOD.
 * Mirrors `WodRequest`.
 */
export interface WodRequest {
  title: string;
  wodType: WodType;
  scoreType: ScoreType;
  description?: string;
  notes?: string;
  authorId?: number;
  gymId?: number;
  isPublic: boolean;
  timeCapSeconds?: number;
  emomInterval?: number;
  emomRounds?: number;
  repScheme?: string;
  movements: WodMovementRequest[];
}

/**
 * DTO representing the full details of a WOD.
 * Mirrors `WodResponse`.
 */
export interface WodResponse {
  id: number;
  title: string;
  wodType: WodType;
  scoreType: ScoreType;
  authorId?: number;
  gymId?: number;
  isPublic: boolean;
  description?: string;
  notes?: string;
  timeCapSeconds?: number;
  emomInterval?: number;
  emomRounds?: number;
  repScheme?: string;
  modalities: Modality[];
  movements: WodMovementResponse[];
  createdAt: string;
  updatedAt: string;
}

/**
 * Lightweight DTO for listing WODs.
 * Mirrors `WodSummaryResponse`.
 */
export interface WodSummaryResponse {
  id: number;
  title: string;
  wodType: WodType;
  scoreType: ScoreType;
  repScheme?: string;
  timeCapSeconds?: number;
  createdAt: string;
}

/**
 * DTO for specifying a single movement within a WOD creation or update request.
 * Mirrors `WodMovementRequest`.
 */
export interface WodMovementRequest {
  movementId: string;
  orderIndex: number;
  repsScheme?: string;
  weight?: number;
  weightUnit?: Unit;
  durationSeconds?: number;
  durationDisplayUnit?: Unit;
  distance?: number;
  distanceUnit?: Unit;
  calories?: number;
  notes?: string;
  scalingOptions?: string;
}

/**
 * Nested DTO for WodResponse containing prescription details.
 * Mirrors `WodMovementResponse`.
 */
export interface WodMovementResponse {
  id: number;
  orderIndex: number;
  repsScheme?: string;
  weight?: number;
  weightUnit?: Unit;
  durationSeconds?: number;
  durationDisplayUnit?: Unit;
  distance?: number;
  distanceUnit?: Unit;
  calories?: number;
  notes?: string;
  scalingOptions?: string;
  movement: MovementResponse;
}


