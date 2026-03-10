import {MuscleResponse} from './muscle.model';

export interface MovementReferenceData {
  categoryGroups: Record<string, string[]>;
  equipmentGroups: Record<string, string[]>;
  techniqueGroups: Record<string, string[]>;
}

/**
 * DTO for creating or updating a Movement in the catalog.
 * Mirrors `MovementRequest`.
 */
export interface MovementRequest {
  name: string;
  nameAbbreviation?: string;
  category: string;
  equipment: string[];
  techniques: string[];
  muscles?: MovementMuscleRequest[];
  involvesBodyweight: boolean;
  bodyweightFactor?: number;
  descriptionEn?: string;
  descriptionFr?: string;
  coachingCuesEn?: string;
  coachingCuesFr?: string;
  videoUrl?: string;
  imageUrl?: string;
}

/**
 * Full DTO for Movement display.
 * Mirrors `MovementResponse`.
 */
export interface MovementResponse {
  id: number;
  name: string;
  nameAbbreviation?: string;
  category: string;
  equipment: string[];
  techniques: string[];
  targetedMuscles: MovementMuscleResponse[];
  involvesBodyweight: boolean;
  bodyweightFactor: number;
  loadBearing: boolean;
  descriptionEn?: string;
  descriptionFr?: string;
  coachingCuesEn?: string;
  coachingCuesFr?: string;
  videoUrl?: string;
  imageUrl?: string;
}

/**
 * Lightweight DTO for listing movements.
 * Mirrors `MovementSummaryResponse`.
 */
export interface MovementSummaryResponse {
  id: number;
  name: string;
  nameAbbreviation?: string;
  category: string;
  imageUrl?: string;
}

export interface MovementMuscleRequest {
  muscleId: number;
  role: string;
  impactFactor?: number;
}

export interface MovementMuscleResponse {
  muscle: MuscleResponse;
  role: string;
  impactFactor: number;
}
