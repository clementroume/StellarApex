import {MovementResponse} from './movement.model';

/**
 * DTO for creating or updating a WOD.
 * Mirrors `WodRequest`.
 */
export interface WodRequest {
  title: string;
  wodType: string;
  scoreType: string;
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
  wodType: string;
  scoreType: string;
  authorId?: number;
  gymId?: number;
  isPublic: boolean;
  description?: string;
  notes?: string;
  timeCapSeconds?: number;
  emomInterval?: number;
  emomRounds?: number;
  repScheme?: string;
  modalities: string[];
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
  wodType: string;
  scoreType: string;
  repScheme?: string;
  timeCapSeconds?: number;
  createdAt: string;
}

/**
 * DTO for specifying a single movement within a WOD creation or update request.
 * Mirrors `WodMovementRequest`.
 */
export interface WodMovementRequest {
  movementId: number;
  orderIndex: number;
  repsScheme?: string;
  weight?: number;
  weightUnit?: string;
  durationSeconds?: number;
  durationDisplayUnit?: string;
  distance?: number;
  distanceUnit?: string;
  calories?: number;
  equipment?: string[];
  techniques?: string[];
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
  weightUnit?: string;
  durationSeconds?: number;
  durationDisplayUnit?: string;
  distance?: number;
  distanceUnit?: string;
  calories?: number;
  equipment?: string[];
  techniques?: string[];
  notes?: string;
  scalingOptions?: string;
  movement: MovementResponse;
}

export interface WodReferenceData {
  wodTypes: string[];
  scoreTypes: string[];
  unitGroups: Record<string, string[]>;
}


