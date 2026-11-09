import {boardName, pluralize} from './movement-board-name';

describe('boardName', () => {
  const name = (movement: string, equipment: string[] = [], techniques: string[] = [], plural = true) =>
    boardName({name: movement, equipment, techniques}, plural);

  it('should build the variants into the name in CrossFit English', () => {
    expect(name('Power Clean', [], ['HANG'])).toBe('Hang Power Cleans');
    expect(name('Muscle-up', ['PULL_UP_BAR'])).toBe('Bar Muscle-Ups');
    expect(name('Muscle-up', ['RINGS'])).toBe('Ring Muscle-Ups');
    expect(name('Burpee Muscle-up', ['PULL_UP_BAR'])).toBe('Burpee Bar Muscle-Ups');
    expect(name('Snatch', ['DUMBBELL'], ['ALTERNATING'])).toBe('Alternating Dumbbell Snatches');
    expect(name('Clean and Jerk', ['DUMBBELL'], ['HANG', 'ONE_ARM'])).toBe('Single-Arm Dumbbell Hang Clean and Jerks');
    expect(name('Lunge', ['BARBELL'], ['OVERHEAD', 'WALK'])).toBe('Barbell Overhead Walking Lunges');
    expect(name('Dip', ['RINGS'], ['STRICT'])).toBe('Strict Ring Dips');
    expect(name('Push-up', [], ['HAND_RELEASE'])).toBe('Hand-Release Push-Ups');
    expect(name('Toes-to-Bar', ['RINGS'])).toBe('Toes-to-Rings');
    expect(name('Pull-up', ['PULL_UP_BAR'], ['STRICT'])).toBe('Strict Pull-Ups');
    expect(name('Carry', ['DUMBBELL'], ['FARMERS_CARRY'], false)).toBe("Dumbbell Farmer's Carry");
  });

  it('should not repeat equipment the name already says, and name machines and runs', () => {
    expect(name('Box Step-up', ['BOX'])).toBe('Box Step-Ups');
    expect(boardName({name: 'Wall-ball Shot', allowed: ['MEDICINE_BALL'], equipment: ['MEDICINE_BALL']}, true))
      .toBe('Wall-Ball Shots');
    expect(name('Biking', ['ECHO_BIKE'], [], false)).toBe('Echo Bike');
    expect(name('Rowing', ['ROWER'], [], false)).toBe('Row');
    expect(name('Running', [], ['WEIGHTED'], false)).toBe('Weighted Run');
  });

  it('should write the usual name of the catalog and only what it does not say', () => {
    const usual = (usualNameEn: string, remainingTechniques: string[] = [], remainingEquipment: string[] = []) =>
      ({usualNameEn, remainingTechniques, remainingEquipment});
    expect(boardName({name: 'Burpee', equipment: ['BARBELL'], techniques: ['OVER', 'FACING'], display: usual('Bar-Facing Burpee')}, true))
      .toBe('Bar-Facing Burpees');
    expect(boardName({name: 'Handstand', display: usual('Handstand Hold')})).toBe('Handstand Hold');
    expect(boardName({name: 'Clean', techniques: ['POWER', 'HANG'], display: usual('Power Clean', ['HANG'])}, true)).toBe('Hang Power Cleans');
    expect(boardName({name: 'Clean and Jerk', techniques: ['1:POWER'], display: usual('Clean and Jerk', ['1:POWER'])})).toBe('Power Clean and Jerk');
    expect(boardName({name: 'Snatch', equipment: ['DUMBBELL'], display: usual('Snatch', [], ['DUMBBELL'])}, true)).toBe('Dumbbell Snatches');
  });

  it('should write English plurals', () => {
    expect(pluralize('Push Press')).toBe('Push Presses');
    expect(pluralize('Carry')).toBe('Carries');
    expect(pluralize('Knees-to-Elbows')).toBe('Knees-to-Elbows');
    expect(pluralize('Thruster')).toBe('Thrusters');
  });
});
