import {MuscleBalance} from '../../api/aldebaran/models/balance.model';
import {joinNames, level, zoneSets} from './review.utils';
import {zoneOf} from './muscle-zones';

describe('review utils', () => {
  const muscle = (code: string, moving: number, holding = 0): MuscleBalance =>
    ({muscleId: 1, code, region: 'X', medicalName: code, movingSets: moving, holdingSets: holding});

  it('shades a value on the scale of its tab, nothing at 0', () => {
    expect(level(0, 10)).toBe(0);
    expect(level(0.5, 10)).toBe(1);
    expect(level(5, 10)).toBe(3);
    expect(level(10, 10)).toBe(5);
  });

  it('sums the muscles of a drawn zone; deep muscles are not drawn (D123)', () => {
    expect(zoneOf('quadratus-lumborum')).toBe('lower-back');
    expect(zoneOf('psoas-major')).toBeNull();
    const sums = zoneSets([muscle('rectus-femoris', 2), muscle('vastus-lateralis', 3), muscle('psoas-major', 4)], 'moving');
    expect(sums).toEqual({quadriceps: 5});
  });

  it('joins names for a sentence', () => {
    expect(joinNames(['Squat'], 'et')).toBe('Squat');
    expect(joinNames(['Squat', 'Clean', 'Snatch'], 'et')).toBe('Squat, Clean et Snatch');
  });
});
