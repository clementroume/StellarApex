export interface MuscleRequest {
  // Identification
  medicalName: string;
  // Characteristics (muscleGroup = region; parentId = functional group, null for a group or a standalone muscle)
  muscleGroup: string;
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
  medicalName: string;
  commonNameEn?: string;
  commonNameFr?: string;
}

export interface MuscleResponse {
  // Identification
  id: number;
  medicalName: string;
  // Characteristics
  muscleGroup: string;
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
  muscleGroups: string[];
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
