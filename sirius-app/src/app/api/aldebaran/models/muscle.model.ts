export interface MuscleReferenceData {
  muscleGroups: string[];
  muscleRoles: string[];
}

/**
 * DTO for creating or updating an anatomical muscle entry.
 * Mirrors `MuscleRequest`.
 */
export interface MuscleRequest {
  medicalName: string;
  commonNameEn?: string;
  commonNameFr?: string;
  descriptionEn?: string;
  descriptionFr?: string;
  muscleGroup: string;
  imageUrl?: string;
}

/**
 * DTO representing anatomical muscle reference data.
 * Mirrors `MuscleResponse`.
 */
export interface MuscleResponse {
  id: number;
  medicalName: string;
  commonNameEn?: string;
  commonNameFr?: string;
  descriptionEn?: string;
  descriptionFr?: string;
  muscleGroup: string;
  imageUrl?: string;
}
