import {MovementRecords, VariantRecords} from '../../api/aldebaran/models/record.model';
import {
  baseVariant, crossfitTotal, familyOf, isRecent, liftSummary, percentBase, percentLoadKg, rmRows, variantLabel
} from './record.utils';

const entry = (reps: number, weightKg: number, date = '2026-11-01', previousWeightKg: number | null = null) =>
  ({reps, weightKg, previousWeightKg, date, scoreId: reps, wodId: 1, wodTitle: 'W'});
const variant = (records: ReturnType<typeof entry>[], estimate?: number, estimates: {reps: number; weightKg: number}[] = []): VariantRecords =>
  ({variantKey: '', base: true, equipment: [], techniques: [], estimatedOneRepMaxKg: estimate ?? null, records, estimates});
const records = (name: string, id: number, base: [number, number][], estimate?: number): MovementRecords => ({
  movementId: id, movementName: name, category: 'SQUAT',
  variants: [
    variant(base.map(([r, w]) => entry(r, w)), estimate),
    {variantKey: ';PAUSED', base: false, equipment: [], techniques: ['PAUSED'], records: [entry(1, 200)], estimates: []}
  ]
});
const today = new Date('2026-11-06T12:00:00');

describe('record utils', () => {
  const squat = records('Back Squat', 1, [[3, 120], [5, 110]], 132);

  it('should find the base version only', () => {
    expect(baseVariant([squat], 1)?.base).toBe(true);
    expect(baseVariant([squat], 2)).toBeUndefined();
  });

  it('should base a percentage on the real N RM, else the estimated 1RM, never on a variant', () => {
    expect(percentBase([squat], 1, 3)).toEqual({kg: 120, estimated: false, date: '2026-11-01'});
    expect(percentBase([squat], 1, 1)).toEqual({kg: 132, estimated: true});
    expect(percentBase([squat], 1, 2)).toBeNull();
    expect(percentBase(null, 1, 1)).toBeNull();
    expect(percentLoadKg({kg: 140, estimated: false}, 80)).toBeCloseTo(112);
  });

  it('should map catalogue categories to families', () => {
    expect(familyOf('DEADLIFT')).toBe('HINGE');
    expect(familyOf('SNATCH')).toBe('WEIGHTLIFTING');
    expect(familyOf('PULLING')).toBe('OTHER');
    expect(familyOf(null)).toBe('OTHER');
  });

  it('should summarise a lift: real 1RM with its recent gain, else the estimate, else the best record', () => {
    const fresh = liftSummary(variant([entry(1, 145, '2026-10-30', 140)]), today)!;
    expect(fresh).toMatchObject({kg: 145, estimated: false, recent: true, gainKg: 5});
    expect(liftSummary(variant([entry(1, 145, '2026-01-01', 140)]), today)).toMatchObject({recent: false, gainKg: null});
    expect(liftSummary(variant([entry(3, 107)], 118), today)).toMatchObject({kg: 118, estimated: true, from: {reps: 3}});
    expect(liftSummary(variant([entry(12, 60)]), today)).toMatchObject({kg: 60, reps: 12, estimated: false});
    expect(liftSummary(variant([]), today)).toBeNull();
    expect(isRecent('2026-10-07', today)).toBe(true);
    expect(isRecent('2026-10-06', today)).toBe(false);
  });

  it('should list records and estimates by number of reps', () => {
    const rows = rmRows(variant([entry(1, 140), entry(5, 115)], undefined, [{reps: 2, weightKg: 131}, {reps: 10, weightKg: 105}]));
    expect(rows.map(r => [r.reps, !!r.record])).toEqual([[1, true], [2, false], [5, true], [10, false]]);
  });

  it('should label a variant with its techniques then its equipment', () => {
    expect(variantLabel({equipment: ['TRAP_BAR'], techniques: ['PAUSED']},
      key => key.split('.').pop()!)).toBe('PAUSED · TRAP_BAR');
  });

  it('should add the three 1RMs of the CrossFit Total, or nothing while one is missing', () => {
    const squat: MovementRecords = {movementId: 1, movementName: 'Squat', category: 'SQUAT', variants: [
      {variantKey: ';BACK_RACK', base: false, equipment: [], techniques: ['BACK_RACK'], records: [entry(1, 140)], estimates: []},
      {variantKey: ';FRONT_RACK', base: false, equipment: [], techniques: ['FRONT_RACK'], records: [entry(1, 110)], estimates: []}
    ]};
    const lifts = [squat, records('Shoulder Press', 2, [[1, 60]]), records('Deadlift', 3, [[1, 180]])];
    expect(crossfitTotal(lifts)).toEqual({kg: 380, parts: [
      {name: 'Back Squat', kg: 140}, {name: 'Shoulder Press', kg: 60}, {name: 'Deadlift', kg: 180}]});
    expect(crossfitTotal(lifts.slice(0, 2))).toBeNull();
  });
});
