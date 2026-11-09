import {MuscleResponse} from './muscle.model';

export interface MovementSummaryResponse {
  id: number;
  name: string;
  nameAbbreviation?: string;
  category: string;
  imageUrl?: string;
  /** Steps of a chain, in order. */
  componentNames?: string[] | null;
  /** Movements an "either" movement accepts. */
  acceptedNames?: string[] | null;
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
  // --- Catalog model ---
  impliedEquipment?: string | null;
  impliedRequiresPosition?: boolean;
  requiredRules?: string[];
  dumbbellBases?: DumbbellBase[];
  components?: MovementComponent[];
  acceptedMovementIds?: number[];
  roleAdjustments?: RoleAdjustment[];
  jointActions?: MovementJointAction[];
  usualNames?: UsualName[];
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
  // --- Catalog model ---
  impliedEquipment?: string | null;
  impliedRequiresPosition?: boolean;
  requiredRules?: string[];
  dumbbellBases?: DumbbellBase[];
  components?: MovementComponent[];
  acceptedMovements?: AcceptedMovement[];
  roleAdjustments?: RoleAdjustment[];
  jointActions?: MovementJointAction[];
  usualNames?: UsualName[];
}

/** Number of dumbbells of a line without laterality; an empty position = no position, null count = free. */
export interface DumbbellBase {
  loadPosition: string;
  implementCount: number | null;
}

/** One step of a chained movement (MovementComponentDto). */
export interface MovementComponent {
  componentOrder: number;
  movementId: number;
  movementName?: string;
  countsForRecords: boolean;
  techniques?: string[];
  equipment?: string[];
}

export interface AcceptedMovement {
  id: number;
  name: string;
}

/** A technique or equipment that adds a muscle or changes its role (RoleAdjustmentDto). */
export interface RoleAdjustment {
  triggerKind: 'TECHNIQUE' | 'EQUIPMENT';
  trigger: string;
  muscleId: number;
  muscleName?: string;
  role: string;
}

/** What a joint does in a movement; an empty trigger = the base movement. */
export interface MovementJointAction {
  trigger: string;
  action: string;
  regime: 'DYNAMIC' | 'HELD';
}

/** A usual name of a movement (UsualNameDto); a chain component's technique is written `1:POWER`. */
export interface UsualName {
  nameEn: string;
  nameFr?: string | null;
  searchTerms?: string | null;
  techniques?: string[];
  equipment?: string[];
  descriptionEn?: string | null;
  descriptionFr?: string | null;
  videoUrl?: string | null;
}

/** One entry of the movement search (UsualNameOptionResponse). */
export interface UsualNameOption {
  movementId: number;
  movementName: string;
  category: string;
  nameEn: string;
  nameFr?: string | null;
  searchTerms?: string | null;
  techniques: string[];
  equipment: string[];
  impliedEquipment?: string | null;
}

/** A muscle of a chosen variant (VariantMuscleResponse). */
export interface VariantMuscle {
  muscleId: number;
  medicalName: string;
  commonNameEn?: string | null;
  commonNameFr?: string | null;
  role: 'AGONIST' | 'SYNERGIST' | 'STABILIZER';
}

/** A movement for a chosen variant (MovementVariantResponse). */
export interface MovementVariant {
  usualNameEn?: string | null;
  usualNameFr?: string | null;
  descriptionEn?: string | null;
  descriptionFr?: string | null;
  videoUrl?: string | null;
  implementCount?: number | null;
  variantKey: string;
  muscles: VariantMuscle[];
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
  // Technique types a line can only hold one value of
  exclusiveTechniqueSets: string[][];
}
