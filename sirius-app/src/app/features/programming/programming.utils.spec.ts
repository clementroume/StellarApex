import {describe, expect, it} from 'vitest';
import {AXES, daysLabel, decimal, detailsOf, percent, radarPoint, radarScale, sortDays} from './programming.utils';

describe('programming.utils', () => {
  it('lists the axes in the backend order, with details only where they exist', () => {
    expect(AXES).toEqual(['SQUAT', 'HINGE', 'PUSH', 'PULL', 'LUNGE', 'LOCOMOTION', 'CORE']);
    expect(detailsOf('SQUAT')).toEqual([]);
    expect(detailsOf('PUSH')).toEqual(['PUSH_V', 'PUSH_H']);
    expect(detailsOf('CORE')).toHaveLength(4);
    expect(detailsOf('LOCOMOTION')).toContain('JUMP');
  });

  it('scales the radar to 30 % at least, else the largest share rounded up to 10', () => {
    expect(radarScale([12, 18])).toBe(30);
    expect(radarScale([25.5, 31])).toBe(40);
    expect(radarScale([])).toBe(30);
  });

  it('places the first axis at the top and clips a share beyond the scale', () => {
    expect(radarPoint(0, 4, 30, 30, 150, 90)).toEqual([150, 60]);
    expect(radarPoint(1, 4, 15, 30, 150, 90)).toEqual([195, 150]);
    expect(radarPoint(0, 4, 60, 30, 150, 90)).toEqual([150, 60]);
  });

  it('formats shares and days in the language shown', () => {
    expect(percent(18, 'fr')).toBe('18,0 %');
    expect(percent(8.94, 'en')).toBe('8.9 %');
    expect(decimal(3.25, 'fr')).toBe('3,3');
    expect(sortDays(['FRIDAY', 'MONDAY', 'FRIDAY'])).toEqual(['MONDAY', 'FRIDAY']);
    expect(daysLabel(['WEDNESDAY', 'MONDAY'], 'en')).toBe('Mon · Wed');
  });
});
