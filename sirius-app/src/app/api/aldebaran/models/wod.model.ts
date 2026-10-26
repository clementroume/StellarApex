import {MovementResponse} from './movement.model';

export type WodScope = 'ALL' | 'MINE' | 'GYM' | 'PUBLIC';

export interface WodSummaryResponse {
  id: number;
  title: string;
  // Format (FOR_TIME, AMRAP, EMOM...) and optional category (GIRLS, HERO, OPEN...)
  wodType: string;
  category?: string | null;
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
  // WOD formats and categories present for the current filters (those two filters are ignored)
  wodTypes: string[];
  categories: string[];
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
  category?: string | null;
  scoreType: string;
  // --- Authorship and Visibility ---
  authorId?: number;
  gymId?: number;
  isPublic: boolean;
  // --- Description and Notes (EN / FR, either may be empty) ---
  descriptionEn?: string;
  descriptionFr?: string;
  notesEn?: string;
  notesFr?: string;
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
  category?: string | null;
  scoreType: string;
  // --- Authorship and Visibility ---
  authorId?: number;
  gymId?: number;
  isPublic: boolean;
  // --- Description and Notes (EN / FR, either may be empty) ---
  descriptionEn?: string;
  descriptionFr?: string;
  notesEn?: string;
  notesFr?: string;
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
  // Men's load, women's load in the same unit
  weight?: number;
  weightFemale?: number;
  weightUnit?: string;
  durationSeconds?: number;
  durationDisplayUnit?: string;
  distance?: number;
  distanceUnit?: string;
  calories?: number;
  caloriesFemale?: number;
  // Box or target height (men's, women's), in heightUnit
  height?: number;
  heightFemale?: number;
  heightUnit?: string;
  // --- Characteristics ---
  equipment?: string[];
  techniques?: string[];
  // --- Instructions (EN / FR) ---
  notesEn?: string;
  notesFr?: string;
  scalingOptionsEn?: string;
  scalingOptionsFr?: string;
}

export interface WodMovementResponse {
  // --- Identification ---
  id: number;
  movement: MovementResponse;
  orderIndex: number;
  // --- Prescription ---
  repsScheme?: string;
  // Men's load, women's load in the same unit
  weight?: number;
  weightFemale?: number;
  weightUnit?: string;
  durationSeconds?: number;
  durationDisplayUnit?: string;
  distance?: number;
  distanceUnit?: string;
  calories?: number;
  caloriesFemale?: number;
  // Box or target height (men's, women's), in heightUnit
  height?: number;
  heightFemale?: number;
  heightUnit?: string;
  // --- Characteristics ---
  equipment?: string[];
  techniques?: string[];
  // --- Instructions (EN / FR) ---
  notesEn?: string;
  notesFr?: string;
  scalingOptionsEn?: string;
  scalingOptionsFr?: string;
}

export interface WodReferenceData {
  // --- Wod Formats and Categories ---
  wodTypes: string[];
  categories: string[];
  // --- Score Types ---
  scoreTypes: string[];
  // --- Units ---
  unitGroups: Record<string, string[]>;
}


