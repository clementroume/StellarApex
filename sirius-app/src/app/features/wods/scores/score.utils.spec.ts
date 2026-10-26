import {vi} from 'vitest';
import {ScoreResponse} from '../../../api/aldebaran/models/score.model';
import {UnitService} from '../../../core/services/unit.service';
import {formatScore, splitRounds, Translate} from './score.utils';

describe('formatScore', () => {
  const t: Translate = (key, params) => params ? `${key} ${JSON.stringify(params)}` : key;
  const units = {
    formatScoreLoad: vi.fn((v: number, u: string) => `${v} ${u}`),
    formatDistance: vi.fn((v: number, _f: null, u: string) => `${v} ${u}`)
  } as unknown as UnitService;
  const score = (overrides: Partial<ScoreResponse>): ScoreResponse => ({
    id: 1, userId: 1, date: '2026-10-26', wodSummary: {} as any, scaling: 'RX', timeCapped: false,
    personalRecord: false, loggedAt: '', ...overrides
  });

  it('should write a time, or the reps at the cap', () => {
    expect(formatScore(score({timeSeconds: 272}), {scoreType: 'TIME'}, units, t)).toBe('4:32');
    expect(formatScore(score({timeCapped: true, reps: 45}), {scoreType: 'TIME'}, units, t))
      .toBe('SCORE.DISPLAY.CAPPED {"reps":45}');
  });

  it('should write total reps, also as rounds + reps when the WOD has a fixed round', () => {
    const cindy = {scoreType: 'REPS', repsPerRound: 30};
    expect(formatScore(score({reps: 312}), cindy, units, t))
      .toBe('SCORE.DISPLAY.REPS {"reps":312} · SCORE.DISPLAY.ROUNDS_REPS {"rounds":10,"reps":12}');
    expect(formatScore(score({reps: 300}), cindy, units, t))
      .toBe('SCORE.DISPLAY.REPS {"reps":300} · SCORE.DISPLAY.ROUNDS {"rounds":10}');
    expect(splitRounds(29, 30)).toEqual({rounds: 0, reps: 29});
    expect(splitRounds(29, null)).toBeNull();
  });

  it('should convert loads and distances through the UnitService', () => {
    expect(formatScore(score({maxWeight: 102.5, weightUnit: 'KG'}), {scoreType: 'WEIGHT'}, units, t)).toBe('102.5 KG');
    expect(formatScore(score({totalLoad: 3000, weightUnit: 'LBS'}), {scoreType: 'LOAD'}, units, t)).toBe('3000 LBS');
    expect(formatScore(score({totalDistance: 5, distanceUnit: 'KILOMETERS'}), {scoreType: 'DISTANCE'}, units, t))
      .toBe('5 KILOMETERS');
  });

  it('should write the rounds completed on the minute of an EMOM', () => {
    expect(formatScore(score({rounds: 24}), {scoreType: 'ROUNDS'}, units, t)).toBe('SCORE.DISPLAY.ROUNDS {"rounds":24}');
  });

  it('should write reps, calories and a done WOD', () => {
    expect(formatScore(score({reps: 150}), {scoreType: 'REPS'}, units, t)).toBe('SCORE.DISPLAY.REPS {"reps":150}');
    expect(formatScore(score({totalCalories: 86}), {scoreType: 'CALORIES'}, units, t)).toBe('86 cal');
    expect(formatScore(score({}), {scoreType: 'NONE'}, units, t)).toBe('SCORE.DISPLAY.DONE');
  });
});
