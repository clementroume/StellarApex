import {MovementRecords, RecordEntry, RecordSource, VariantRecords} from '../../api/aldebaran/models/record.model';

/** Base of a percentage load: a real N RM (with its day), or the estimated 1RM (D58). */
export interface PercentBase {
  kg: number;
  estimated: boolean;
  date?: string;
}

/** Families of the records board (D64), from the catalogue categories. */
export type LiftFamily = 'SQUAT' | 'HINGE' | 'PRESS' | 'WEIGHTLIFTING' | 'OTHER';
export const LIFT_FAMILIES: LiftFamily[] = ['SQUAT', 'HINGE', 'PRESS', 'WEIGHTLIFTING', 'OTHER'];

const FAMILY_OF: Record<string, LiftFamily> = {
  SQUAT: 'SQUAT',
  DEADLIFT: 'HINGE',
  PRESS_AND_JERK: 'PRESS',
  CLEAN: 'WEIGHTLIFTING',
  SNATCH: 'WEIGHTLIFTING',
  COMPLEXES: 'WEIGHTLIFTING'
};

/** Family of a catalogue category; anything else is "other". */
export function familyOf(category: string | null | undefined): LiftFamily {
  return (category && FAMILY_OF[category]) || 'OTHER';
}

/** Days a record stays "new" on the board (PR badge, gain). */
export const NEW_RECORD_DAYS = 30;

/** The base version of a movement in the user's records, if any record exists for it. */
export function baseVariant(records: MovementRecords[] | null | undefined, movementId: number): VariantRecords | undefined {
  return records?.find(m => m.movementId === movementId)?.variants.find(v => v.base);
}

/**
 * What a percentage refers to (D58-D59): the record of `rmReps` reps of the variant the catalog names,
 * else, for a 1RM, the estimated 1RM; null when the athlete has neither (no fallback to another variant).
 */
export function percentBase(records: MovementRecords[] | null | undefined, movementId: number,
                            rmReps: number | null | undefined, variantKey: string = ''): PercentBase | null {
  const variant = records?.find(m => m.movementId === movementId)?.variants.find(v => v.variantKey === variantKey);
  if (!variant) return null;
  const reps = rmReps ?? 1;
  const record = variant.records.find(r => r.reps === reps);
  if (record) return {kg: record.weightKg, estimated: false, date: record.date};
  if (reps === 1 && variant.estimatedOneRepMaxKg) return {kg: variant.estimatedOneRepMaxKg, estimated: true};
  return null;
}

/** The exact load of a percentage, in kg (rounded for display by `UnitService.formatPercentLoad`). */
export function percentLoadKg(base: PercentBase, percent: number): number {
  return base.kg * percent / 100;
}

/** "Pause · Trap bar": the variant label shown under the base version, from the catalogue translations. */
export function variantLabel(variant: Pick<VariantRecords, 'techniques' | 'equipment'>, translate: (key: string) => string): string {
  return [
    // A chain's component technique is written 1:POWER
    ...variant.techniques.map(t => translate('MOVEMENT.TECHNIQUES.' + t.replace(/^\d+:/, ''))),
    ...variant.equipment.map(e => translate('MOVEMENT.EQUIPMENT.' + e))
  ].join(' · ');
}

/** "Clean · Power": what an inherited record really is. */
export function sourceLabel(source: RecordSource, translate: (key: string) => string): string {
  const label = variantLabel(source, translate);
  return label ? `${source.movementName} · ${label}` : source.movementName;
}

/** The three lifts of the CrossFit Total, in order. */
export const TOTAL_LIFTS = [
  {name: 'Back Squat', movement: 'Squat', variantKey: ';BACK_RACK'},
  {name: 'Shoulder Press', movement: 'Shoulder Press', variantKey: ''},
  {name: 'Deadlift', movement: 'Deadlift', variantKey: ''}
];

/** CrossFit Total from the 1RMs of the base versions (D53), with each lift, or null while one is missing. */
export function crossfitTotal(records: MovementRecords[] | null | undefined): {kg: number; parts: {name: string; kg: number}[]} | null {
  const parts: {name: string; kg: number}[] = [];
  for (const {name, movement, variantKey} of TOTAL_LIFTS) {
    const variant = records?.find(m => m.movementName === movement)?.variants.find(v => v.variantKey === variantKey);
    const oneRm = variant?.records.find(r => r.reps === 1);
    if (!oneRm) return null;
    parts.push({name, kg: oneRm.weightKg});
  }
  return {kg: parts.reduce((sum, p) => sum + p.kg, 0), parts};
}

/** Whether a record was set within the last `NEW_RECORD_DAYS` days. */
export function isRecent(date: string, today: Date = new Date()): boolean {
  const midnight = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  const days = Math.round((midnight.getTime() - new Date(`${date}T00:00:00`).getTime()) / 86_400_000);
  return days <= NEW_RECORD_DAYS;
}

/** What a tile of the board shows for a movement (D64): the real 1RM, else the estimated one, else its best record. */
export interface LiftSummary {
  kg: number;
  reps: number;
  estimated: boolean;
  record?: RecordEntry;
  // The record it beat, when recent
  gainKg?: number | null;
  recent: boolean;
  // The record the estimate comes from ("3RM 107")
  from?: RecordEntry;
}

export function liftSummary(variant: VariantRecords | undefined, today: Date = new Date()): LiftSummary | null {
  if (!variant || variant.records.length === 0) return null;
  const oneRm = variant.records.find(r => r.reps === 1);
  if (oneRm) {
    const recent = isRecent(oneRm.date, today);
    return {
      kg: oneRm.weightKg, reps: 1, estimated: false, record: oneRm, recent,
      gainKg: recent && oneRm.previousWeightKg != null ? oneRm.weightKg - oneRm.previousWeightKg : null
    };
  }
  const from = [...variant.records].sort((a, b) => a.reps - b.reps)[0];
  if (variant.estimatedOneRepMaxKg) {
    return {kg: variant.estimatedOneRepMaxKg, reps: 1, estimated: true, recent: isRecent(from.date, today), from};
  }
  return {kg: from.weightKg, reps: from.reps, estimated: false, record: from, recent: isRecent(from.date, today)};
}

/** One line of the RM table of a lift: a record, or an estimate shown muted (D63). */
export interface RmRow {
  reps: number;
  kg: number;
  record?: RecordEntry;
}

/** RM table of a variant: the usual N RM (records or estimates) and every other N with a record, by N. */
export function rmRows(variant: VariantRecords | undefined): RmRow[] {
  if (!variant) return [];
  const rows: RmRow[] = [
    ...variant.records.map(r => ({reps: r.reps, kg: r.weightKg, record: r})),
    ...(variant.estimates ?? []).map(e => ({reps: e.reps, kg: e.weightKg}))
  ];
  return rows.sort((a, b) => a.reps - b.reps);
}

/** Percentages of the % table of a lift. */
export const PERCENT_STEPS = [50, 60, 65, 70, 75, 80, 85, 90, 95];
