import {MovementRecords} from '../../api/aldebaran/models/record.model';
import {baseVariant, crossfitTotal, percentBase, percentLoadKg, variantLabel} from './record.utils';

const entry = (reps: number, weightKg: number) => ({reps, weightKg, date: '2026-11-01', scoreId: reps, wodId: 1, wodTitle: 'W'});
const records = (name: string, id: number, base: [number, number][], estimate?: number): MovementRecords => ({
  movementId: id, movementName: name,
  variants: [
    {variantKey: '', base: true, equipment: [], techniques: [], estimatedOneRepMaxKg: estimate ?? null,
      records: base.map(([r, w]) => entry(r, w))},
    {variantKey: ';PAUSED', base: false, equipment: [], techniques: ['PAUSED'], records: [entry(1, 200)]}
  ]
});

describe('record utils', () => {
  const squat = records('Back Squat', 1, [[3, 120], [5, 110]], 132);

  it('should find the base version only', () => {
    expect(baseVariant([squat], 1)?.base).toBe(true);
    expect(baseVariant([squat], 2)).toBeUndefined();
  });

  it('should base a percentage on the real N RM, else the estimated 1RM, never on a variant', () => {
    expect(percentBase([squat], 1, 3)).toEqual({kg: 120, estimated: false});
    expect(percentBase([squat], 1, 1)).toEqual({kg: 132, estimated: true});
    expect(percentBase([squat], 1, 2)).toBeNull();
    expect(percentBase([squat], 9, 1)).toBeNull();
    expect(percentBase(null, 1, 1)).toBeNull();
    const withOneRm = records('Back Squat', 1, [[1, 140]], 132);
    expect(percentBase([withOneRm], 1, null)).toEqual({kg: 140, estimated: false});
    expect(percentLoadKg({kg: 140, estimated: false}, 80)).toBeCloseTo(112);
  });

  it('should label a variant with its techniques then its equipment', () => {
    expect(variantLabel({variantKey: 'TRAP_BAR;PAUSED', base: false, equipment: ['TRAP_BAR'], techniques: ['PAUSED'], records: []},
      key => key.split('.').pop()!)).toBe('PAUSED · TRAP_BAR');
  });

  it('should add the three 1RMs of the CrossFit Total, or nothing while one is missing', () => {
    const lifts = [records('Back Squat', 1, [[1, 140]]), records('Shoulder Press', 2, [[1, 60]]), records('Deadlift', 3, [[1, 180]])];
    expect(crossfitTotal(lifts)).toEqual({kg: 380});
    expect(crossfitTotal(lifts.slice(0, 2))).toBeNull();
  });
});
