import {formatDuration, formatTimeCapShort, localizedText} from './wod.utils';

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

describe('formatTimeCapShort', () => {
  it('should write whole minutes with a quote and keep the seconds otherwise', () => {
    expect(formatTimeCapShort(900)).toBe("15'");
    expect(formatTimeCapShort(425)).toBe("7'05");
  });

  it('should return an empty string without a time cap', () => {
    expect(formatTimeCapShort(undefined)).toBe('');
    expect(formatTimeCapShort(0)).toBe('');
  });
});

describe('localizedText', () => {
  it('should pick the user language and fall back on the other one', () => {
    expect(localizedText('fr', 'For time', 'Pour le temps')).toBe('Pour le temps');
    expect(localizedText('en', 'For time', 'Pour le temps')).toBe('For time');
    expect(localizedText('fr', 'For time', null)).toBe('For time');
    expect(localizedText('en', '', 'Pour le temps')).toBe('Pour le temps');
    expect(localizedText('en', null, null)).toBe('');
  });
});
