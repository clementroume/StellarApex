import {addDays, blockTheme, isTextKind, localDate, mondayOf, sessionDateLabel, sessionLink, sortSessions} from './session.utils';

describe('session utils', () => {
  it('should compute local days and the Monday of a week', () => {
    expect(localDate(new Date(2026, 10, 3, 23, 30))).toBe('2026-11-03');
    expect(addDays('2026-10-31', 2)).toBe('2026-11-02');
    expect(mondayOf('2026-11-04')).toBe('2026-11-02');
    expect(mondayOf('2026-11-08')).toBe('2026-11-02');
    expect(mondayOf('2026-11-02')).toBe('2026-11-02');
  });

  it('should label a WOD session by its day and a specialty by its week', () => {
    const t = (key: string, p?: object) => `${key} ${JSON.stringify(p)}`;
    expect(sessionDateLabel({type: 'WOD', date: '2026-11-03', endDate: '2026-11-03'}, 'en', t)).toContain('November 3');
    expect(sessionDateLabel({type: 'SPECIALTY', date: '2026-11-02', endDate: '2026-11-08'}, 'en', t))
      .toBe('SESSION.WEEK_OF {"from":"Nov 2","to":"Nov 8"}');
  });

  it('should pick the theme in the language shown and link personal sessions to My training', () => {
    expect(blockTheme('fr', {themeEn: 'Heavy snatch', themeFr: 'Snatch lourd'})).toBe('Snatch lourd');
    expect(blockTheme('fr', {themeEn: 'Heavy snatch'})).toBe('Heavy snatch');
    expect(isTextKind('WARM_UP')).toBe(true);
    expect(isTextKind('SKILL')).toBe(false);
    expect(sessionLink({id: 4, gymId: null})).toEqual(['/training/sessions', '4']);
    expect(sessionLink({id: 4, gymId: 9})).toEqual(['/box/sessions', '4']);
  });

  it('should list WOD sessions by day before the specialties', () => {
    const s = (id: number, type: 'WOD' | 'SPECIALTY', date: string) => ({id, type, date} as any);
    expect(sortSessions([s(1, 'SPECIALTY', '2026-11-02'), s(2, 'WOD', '2026-11-04'), s(3, 'WOD', '2026-11-03')])
      .map(x => x.id)).toEqual([3, 2, 1]);
  });
});
