import {formatDuration} from './wod.utils';

describe('formatDuration', () => {
  it('should format seconds as m:ss', () => {
    expect(formatDuration(754)).toBe('12:34');
    expect(formatDuration(60)).toBe('1:00');
    expect(formatDuration(5)).toBe('0:05');
  });

  it('should return an empty string for missing or negative values', () => {
    expect(formatDuration(undefined)).toBe('');
    expect(formatDuration(null)).toBe('');
    expect(formatDuration(-1)).toBe('');
  });
});
