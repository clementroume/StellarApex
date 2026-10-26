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
