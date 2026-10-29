import {defaultExtras, defaultMeasure, ladderValues, parseSets, roundsOf, scoreTypeOf} from './wod-quick.utils';

describe('wod-quick utils', () => {
  it('should pick the usual measure of a movement', () => {
    expect(defaultMeasure('Running', 'CARDIO')).toBe('DISTANCE');
    expect(defaultMeasure('Handstand Walk', 'INVERTED')).toBe('DISTANCE');
    expect(defaultMeasure('Plank', 'CORE')).toBe('TIME');
    expect(defaultMeasure('Rowing', 'CARDIO_MACHINES')).toBe('CALORIES');
    expect(defaultMeasure('Sled Push', 'SLED')).toBe('DISTANCE');
    expect(defaultMeasure('Jumping Jack', 'CARDIO')).toBe('REPS');
  });

  it('should pick the extra measures of a category', () => {
    expect(defaultExtras('SQUAT')).toEqual(['LOAD']);
    expect(defaultExtras('CARRY')).toEqual(['LOAD']);
    expect(defaultExtras('THROWS_AND_SLAMS')).toEqual(['LOAD', 'HEIGHT']);
    expect(defaultExtras('JUMPING')).toEqual(['HEIGHT']);
    expect(defaultExtras('PULLING')).toEqual([]);
    expect(defaultExtras(null)).toEqual([]);
    expect(defaultExtras('BURPEES', 'Burpee Box Jump')).toEqual(['HEIGHT']);
  });

  it('should read ladders, sets and former rounds', () => {
    expect(ladderValues('21-15-9')).toEqual([21, 15, 9]);
    expect(ladderValues('21')).toBeNull();
    expect(parseSets('5x5')).toEqual({sets: 5, reps: '5'});
    expect(parseSets('5-5-3-3-1')).toEqual({sets: 5, reps: '5-5-3-3-1'});
    expect(parseSets('build to a 1RM')).toBeNull();
    expect(roundsOf('5 Rounds for time')).toBe(5);
    expect(roundsOf('21-15-9')).toBeNull();
  });

  it('should score an ascending ladder and intervals in reps', () => {
    expect(scoreTypeOf('LADDER')).toBe('TIME');
    expect(scoreTypeOf('LADDER', true)).toBe('REPS');
    expect(scoreTypeOf('EMOM')).toBe('ROUNDS');
    expect(scoreTypeOf('EMOM', false, true)).toBe('REPS');
    expect(scoreTypeOf('DEATH_BY')).toBe('ROUNDS');
  });
});
