export type MuscleGroup =
  | 'LEGS'
  | 'BACK'
  | 'CHEST'
  | 'SHOULDERS'
  | 'ARMS'
  | 'CORE';

export type MuscleRole =
  | 'AGONIST'
  | 'SYNERGIST'
  | 'STABILIZER';

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
  muscleGroup: MuscleGroup;
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
  muscleGroup: MuscleGroup;
}
