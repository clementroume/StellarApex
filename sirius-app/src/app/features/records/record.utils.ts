import {MovementRecords, VariantRecords} from '../../api/aldebaran/models/record.model';

/** Base of a percentage load: a real N RM, or the estimated 1RM (D58). */
export interface PercentBase {
  kg: number;
  estimated: boolean;
}

/** The base version of a movement in the user's records, if any record exists for it. */
export function baseVariant(records: MovementRecords[] | null | undefined, movementId: number): VariantRecords | undefined {
  return records?.find(m => m.movementId === movementId)?.variants.find(v => v.base);
}

/**
 * What a percentage refers to (D58-D59): the record of `rmReps` reps of the base version of the movement,
 * else, for a 1RM, the estimated 1RM; null when the athlete has neither (no fallback to another variant).
 */
export function percentBase(records: MovementRecords[] | null | undefined, movementId: number,
                            rmReps: number | null | undefined): PercentBase | null {
  const variant = baseVariant(records, movementId);
  if (!variant) return null;
  const reps = rmReps ?? 1;
  const record = variant.records.find(r => r.reps === reps);
  if (record) return {kg: record.weightKg, estimated: false};
  if (reps === 1 && variant.estimatedOneRepMaxKg) return {kg: variant.estimatedOneRepMaxKg, estimated: true};
  return null;
}

/** The exact load of a percentage, in kg (rounded for display by `UnitService.formatPercentLoad`). */
export function percentLoadKg(base: PercentBase, percent: number): number {
  return base.kg * percent / 100;
}

/** "Pause · Trap bar": the variant label shown under the base version, from the catalogue translations. */
export function variantLabel(variant: VariantRecords, translate: (key: string) => string): string {
  return [
    ...variant.techniques.map(t => translate('MOVEMENT.TECHNIQUES.' + t)),
    ...variant.equipment.map(e => translate('MOVEMENT.EQUIPMENT.' + e))
  ].join(' · ');
}

/** CrossFit Total from the 1RMs of the base versions (D53), or null while one lift is missing. */
export function crossfitTotal(records: MovementRecords[] | null | undefined): {kg: number} | null {
  const lifts = ['Back Squat', 'Shoulder Press', 'Deadlift'];
  let total = 0;
  for (const name of lifts) {
    const variant = records?.find(m => m.movementName === name)?.variants.find(v => v.base);
    const oneRm = variant?.records.find(r => r.reps === 1);
    if (!oneRm) return null;
    total += oneRm.weightKg;
  }
  return {kg: total};
}
