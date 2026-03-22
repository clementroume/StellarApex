import {MuscleResponse} from './muscle.model';

export interface MovementSummaryResponse {
  id: number;
  name: string;
  nameAbbreviation?: string;
  category: string;
  imageUrl?: string;
}

export interface MovementRequest {
  // --- Identification ---
  name: string;
  nameAbbreviation?: string;
  category: string;
  // --- Characteristics ---
  equipment?: string[];
  techniques?: string[];
  muscles?: MovementMuscleRequest[];
  // --- Internationalized Content ---
  descriptionEn?: string;
  descriptionFr?: string;
  coachingCuesEn?: string;
  coachingCuesFr?: string;
  // --- Media ---
  videoUrl?: string;
  imageUrl?: string;
}

export interface MovementResponse {
  // --- Identification ---
  id: number;
  name: string;
  nameAbbreviation?: string;
  category: string;
  // --- Characteristics ---
  equipment: string[];
  techniques: string[];
  targetedMuscles: MovementMuscleResponse[];
  // --- Internationalized Content ---
  descriptionEn?: string;
  descriptionFr?: string;
  coachingCuesEn?: string;
  coachingCuesFr?: string;
  // --- Media ---
  videoUrl?: string;
  imageUrl?: string;
}

export interface MovementMuscleRequest {
  // --- Relationships ---
  muscleId: number;
  role: string;
  impactFactor?: number;
}

export interface MovementMuscleResponse {
  // --- Relationships ---
  muscle: MuscleResponse;
  role: string;
  impactFactor: number;
}

export interface MovementReferenceData {
  // --- Modalities and Categories ---
  categoryGroups: Record<string, string[]>;
  // -- Equipment ---
  equipmentGroups: Record<string, string[]>;
  // -- Techniques ---
  techniqueGroups: Record<string, string[]>;
}
