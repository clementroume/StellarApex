export interface MuscleRequest {
  // Identification
  medicalName: string;
  // Characteristics
  muscleGroup: string;
  // Internationalized Content
  commonNameEn?: string;
  commonNameFr?: string;
  descriptionEn?: string;
  descriptionFr?: string;
  // Media
  imageUrl?: string;
}

export interface MuscleResponse {
  // Identification
  id: number;
  medicalName: string;
  // Characteristics
  muscleGroup: string;
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
