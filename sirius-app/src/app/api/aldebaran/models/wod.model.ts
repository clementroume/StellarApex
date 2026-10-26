import {MovementResponse} from './movement.model';

export type WodScope = 'ALL' | 'MINE' | 'GYM' | 'PUBLIC';

export interface WodSummaryResponse {
  id: number;
  title: string;
  wodType: string;
  scoreType: string;
  repScheme?: string;
  timeCapSeconds?: number;
  authorId?: number;
  gymId?: number;
  isPublic: boolean;
  createdAt: string;
}

/** Values available to filter the WOD list, computed by Aldebaran with the list's security. */
export interface WodFacets {
  // WOD types present for the current filters (the type filter itself is ignored)
  wodTypes: string[];
  // Gyms owning readable WODs and authors of readable personal WODs (admin selectors)
  gymIds: number[];
  authorIds: number[];
}

/** Rights of the current user on a WOD, computed by Aldebaran (WodSecurity). */
export interface WodCapabilities {
  canModify: boolean;
  // The WOD has logged scores: edition and deletion are refused
  locked: boolean;
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
  // --- Rights ---
  capabilities?: WodCapabilities;
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
  // --- Wod Types ---
  wodTypes: string[];
  // --- Score Types ---
  scoreTypes: string[];
  // --- Units ---
  unitGroups: Record<string, string[]>;
}


