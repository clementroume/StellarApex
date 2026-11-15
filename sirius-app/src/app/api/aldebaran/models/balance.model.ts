/** Time domains of the metcons (Aldebaran `TimeDomain`, D86). */
export const TIME_DOMAINS = ['VERY_SHORT', 'SHORT', 'MEDIUM', 'LONG', 'INTERVALS'] as const;
export type TimeDomain = typeof TIME_DOMAINS[number];

/** The three modalities of the CrossFit Level 1. */
export const MODALITIES = ['GYMNASTICS', 'WEIGHTLIFTING', 'MONOSTRUCTURAL'] as const;
export type Modality = typeof MODALITIES[number];

/** Sets of one muscle (MuscleBalance). */
export interface MuscleBalance {
  muscleId: number;
  code?: string | null;
  region: string;
  medicalName: string;
  commonNameEn?: string | null;
  commonNameFr?: string | null;
  /** Sets in movement: agonist 1, synergist 0.5. */
  movingSets: number;
  /** Sets in holding: stabilizer. */
  holdingSets: number;
}

/** Equivalent sets of one week (WeekVolume). */
export interface WeekVolume {
  weekStart: string;
  sets: number;
}

/** Strength sets of one lift (LiftBalance). */
export interface LiftBalance {
  /** CrossFit English name of a base movement, or of a complex. */
  label: string;
  movementId?: number | null;
  sets: number;
  /** One set at 80 % of the 1RM of its day. */
  nearRecord: boolean;
}

/**
 * The current user's weekly balance (WeeklyBalanceResponse, lot D): facts only, never an alert nor
 * a ratio (D89). The sections cover the chosen week and the 3 before; the volume the 7 before.
 */
export interface WeeklyBalance {
  weekStart: string;
  windowStart: string;
  muscles: MuscleBalance[];
  volume: WeekVolume[];
  minutes: Record<TimeDomain, number>;
  lifts: LiftBalance[];
  modalities: Record<Modality, number>;
}
