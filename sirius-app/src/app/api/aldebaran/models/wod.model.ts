import {MovementResponse} from './movement.model';

export interface WodSummaryResponse {
  id: number;
  title: string;
  wodType: string;
  scoreType: string;
  repScheme?: string;
  timeCapSeconds?: number;
  createdAt: string;
}

export interface WodRequest {
  // --- Identification ---
  title: string;
  wodType: string;
  scoreType: string;
  // --- Authorship and Visibility ---
  authorId?: number;
  gymId?: number;
  isPublic: boolean;
  // --- Description and Notes ---
  description?: string;
  notes?: string;
  // --- Structure and Prescription ---
  timeCapSeconds?: number;
  emomInterval?: number;
  emomRounds?: number;
  repScheme?: string;
  movements: WodMovementRequest[];
}

export interface WodResponse {
  // --- Identification ---
  id: number;
  title: string;
  wodType: string;
  scoreType: string;
  // --- Authorship and Visibility ---
  authorId?: number;
  gymId?: number;
  isPublic: boolean;
  // --- Description and Notes ---
  description?: string;
  notes?: string;
  // --- Structure and Prescription ---
  timeCapSeconds?: number;
  emomInterval?: number;
  emomRounds?: number;
  repScheme?: string;
  modalities: string[];
  movements: WodMovementResponse[];
  // --- Audit ---
  createdAt: string;
  updatedAt: string;
}

export interface WodMovementRequest {
  // ---  Identification ---
  movementId: number;
  orderIndex: number;
  // --- Prescription ---
  repsScheme?: string;
  weight?: number;
  weightUnit?: string;
  durationSeconds?: number;
  durationDisplayUnit?: string;
  distance?: number;
  distanceUnit?: string;
  calories?: number;
  // --- Characteristics ---
  equipment?: string[];
  techniques?: string[];
  // --- Instructions ---
  notes?: string;
  scalingOptions?: string;
}

export interface WodMovementResponse {
  // --- Identification ---
  id: number;
  movement: MovementResponse;
  orderIndex: number;
  // --- Prescription ---
  repsScheme?: string;
  weight?: number;
  weightUnit?: string;
  durationSeconds?: number;
  durationDisplayUnit?: string;
  distance?: number;
  distanceUnit?: string;
  calories?: number;
  // --- Characteristics ---
  equipment?: string[];
  techniques?: string[];
  // --- Instructions ---
  notes?: string;
  scalingOptions?: string;
}

export interface WodReferenceData {
  wodTypes: string[];
  scoreTypes: string[];
  unitGroups: Record<string, string[]>;
}


