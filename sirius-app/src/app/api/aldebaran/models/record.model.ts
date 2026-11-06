import {Division} from '../../antares/models/user.model';

/** Age group of an athlete in the year of a score (CrossFit Open brackets, D55). */
export type AgeGroup =
  'AGE_15_AND_UNDER' | 'AGE_16_17' | 'AGE_18_34' | 'AGE_35_39' | 'AGE_40_44' | 'AGE_45_49'
  | 'AGE_50_54' | 'AGE_55_59' | 'AGE_60_64' | 'AGE_65_PLUS';

export const AGE_GROUPS: AgeGroup[] = [
  'AGE_15_AND_UNDER', 'AGE_16_17', 'AGE_18_34', 'AGE_35_39', 'AGE_40_44', 'AGE_45_49',
  'AGE_50_54', 'AGE_55_59', 'AGE_60_64', 'AGE_65_PLUS'
];

/** One record: the best load for a number of reps (mirrors Aldebaran RecordEntryResponse). */
export interface RecordEntry {
  reps: number;
  weightKg: number;
  date: string;
  scoreId: number;
  wodId: number;
  wodTitle: string;
}

/** Records of one variant set of a movement (VariantRecordsResponse). */
export interface VariantRecords {
  // Empty for the base version
  variantKey: string;
  base: boolean;
  equipment: string[];
  techniques: string[];
  // Epley estimate from the records of 2 to 10 reps, displayed only (never a record)
  estimatedOneRepMaxKg?: number | null;
  records: RecordEntry[];
}

/** Records of one movement, base version first (MovementRecordsResponse). */
export interface MovementRecords {
  movementId: number;
  movementName: string;
  variants: VariantRecords[];
}

/** A public "N RM" test of the catalogue (CanonicalLiftResponse). */
export interface CanonicalLift {
  wodId: number;
  movementId: number;
  movementName: string;
  repMax: number;
}

/** One line of a box records board (BoardRecordResponse); the name comes from Antares. */
export interface BoardRecord {
  userId: number;
  reps: number;
  weightKg: number;
  date: string;
  division?: Division | null;
  ageGroup?: AgeGroup | null;
}
