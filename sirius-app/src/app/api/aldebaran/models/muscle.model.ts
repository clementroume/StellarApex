export interface MuscleRequest {
  // Identification
  medicalName: string;
  // Characteristics (parentId = functional group, null for a group or a standalone muscle)
  region: string;
  parentId?: number | null;
  // Internationalized Content
  commonNameEn?: string;
  commonNameFr?: string;
  descriptionEn?: string;
  descriptionFr?: string;
  // Media
  imageUrl?: string;
}

/** Lightweight muscle reference used for functional group links. */
export interface MuscleRefResponse {
  id: number;
  /** Stable key of the muscle in the 3D model; never changes when the muscle is renamed. */
  code: string;
  medicalName: string;
  commonNameEn?: string;
  commonNameFr?: string;
}

export interface MuscleResponse {
  // Identification
  id: number;
  /** Stable key of the muscle in the 3D model; never changes when the muscle is renamed. */
  code: string;
  medicalName: string;
  // Characteristics
  region: string;
  parent?: MuscleRefResponse | null;
  members?: MuscleRefResponse[];
  // Internationalized Content
  commonNameEn?: string;
  commonNameFr?: string;
  descriptionEn?: string;
  descriptionFr?: string;
  // Media
  imageUrl?: string;
}

export interface MuscleReferenceData {
  // --- Muscle Group ---
  regions: string[];
  // --- Muscle Role ---
  muscleRoles: string[];
}

/** A movement that works a muscle (MuscleUsageResponse); `trigger` = the technique or equipment that brings the role. */
export interface MuscleUsage {
  movementId: number;
  movementName: string;
  category: string;
  role: 'AGONIST' | 'SYNERGIST' | 'STABILIZER';
  trigger?: string | null;
}
