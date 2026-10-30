/** WOD categories (formats v3): the first row of chips. */
export type WodCategory = 'FOR_TIME' | 'AMRAP' | 'EMOM' | 'TABATA' | 'FOR_LOAD' | 'FOR_QUALITY';

export const CATEGORIES: WodCategory[] = ['FOR_TIME', 'AMRAP', 'EMOM', 'TABATA', 'FOR_LOAD', 'FOR_QUALITY'];

/** Sub-categories of each category (second row of chips), the usual one first. */
export const STRUCTURES: Record<WodCategory, string[]> = {
  FOR_TIME: ['ROUNDS', 'SINGLE', 'SCHEME', 'CHIPPER', 'LADDER'],
  AMRAP: ['ROUNDS', 'LADDER', 'MAX_REPS'],
  EMOM: ['EVERY_MINUTE', 'EVERY_X', 'INTERVALS', 'DEATH_BY'],
  TABATA: ['CLASSIC', 'CUSTOM'],
  FOR_LOAD: ['SETS', 'SCHEME', 'MAX', 'COMPLEX'],
  FOR_QUALITY: ['PRACTICE']
};

/** Usual score type of a category / sub-category (changeable apart). */
export function scoreTypeOf(category: WodCategory, structure: string): string {
  switch (category) {
    case 'FOR_TIME':
      return 'TIME';
    case 'AMRAP':
    case 'TABATA':
      return 'REPS';
    case 'EMOM':
      return structure === 'INTERVALS' ? 'REPS' : 'ROUNDS';
    case 'FOR_LOAD':
      return 'WEIGHT';
    default:
      return 'NONE';
  }
}

/** Sub-categories whose movements can be split into buy-in, rounds and buy-out (Murph). */
export function withSegments(category: WodCategory, structure: string): boolean {
  return structure === 'ROUNDS' && (category === 'FOR_TIME' || category === 'AMRAP');
}

/** Sub-categories with a time cap or a duration in minutes (For Load › Max: "build to a 1RM in 12 min"). */
export function timed(category: WodCategory, structure: string): boolean {
  return category === 'FOR_TIME' || (category === 'AMRAP' && structure !== 'MAX_REPS')
    || (category === 'FOR_LOAD' && structure === 'MAX');
}

/** Sub-categories written as sets (5x5, 5-5-3-3-1, a complex) in the sets field. */
export function withSets(category: WodCategory): boolean {
  return category === 'FOR_LOAD' || category === 'FOR_QUALITY';
}

/**
 * The quantity of a movement line: reps (a number, "10/side"), max reps, a distance, calories
 * (men / women) or a time. Stored in the existing fields (`repsScheme`, `distance`, `calories`,
 * `durationSeconds`).
 */
export type Measure = 'REPS' | 'MAX' | 'DISTANCE' | 'CALORIES' | 'TIME';

export const MEASURES: Measure[] = ['REPS', 'MAX', 'DISTANCE', 'CALORIES', 'TIME'];

/** A measure prescribed besides the quantity: load, box or target height. */
export type Extra = 'LOAD' | 'HEIGHT';

export const ALL_EXTRAS: Extra[] = ['LOAD', 'HEIGHT'];

const LOAD_CATEGORIES = ['DEADLIFT', 'SQUAT', 'PRESS_AND_JERK', 'CLEAN', 'SNATCH', 'COMPLEXES', 'SWING', 'OTHER_LIFTS',
  'STRONGMAN_LIFTS', 'CARRY', 'SLED'];
// Catalogue movements done over a distance or held for a time (exact catalogue names)
const DISTANCE_MOVEMENTS = ['Running', 'Swimming', 'Handstand Walk', 'Bear Crawl', 'Crab Walk', 'Alligator Walk'];
const TIME_MOVEMENTS = ['Plank', 'L-Sit', 'Hollow Position', 'Arch Position', 'Handstand'];

/**
 * Usual quantity of a movement: a distance for running, swimming, walks, carries and sleds,
 * calories for machines, a time for holds, reps otherwise.
 */
export function defaultMeasure(name: string | null | undefined, category: string | null | undefined): Measure {
  if (name && DISTANCE_MOVEMENTS.includes(name)) return 'DISTANCE';
  if (name && TIME_MOVEMENTS.includes(name)) return 'TIME';
  switch (category) {
    case 'CARDIO_MACHINES':
      return 'CALORIES';
    case 'CARRY':
    case 'SLED':
      return 'DISTANCE';
    default:
      return 'REPS';
  }
}

/**
 * Measures a movement usually needs besides its quantity, from its catalogue category: a load for
 * barbell, odd object lifts, carries and sleds, a load and a target for throws (wall ball), a
 * height for jumps (box jump) and for any movement on a box (burpee box jump). Other measures stay
 * one tap away.
 */
export function defaultExtras(category: string | null | undefined, name?: string | null): Extra[] {
  if (name && /\bbox\b/i.test(name) && category !== 'JUMPING') return ['HEIGHT'];
  if (!category) return [];
  if (LOAD_CATEGORIES.includes(category)) return ['LOAD'];
  if (category === 'THROWS_AND_SLAMS') return ['LOAD', 'HEIGHT'];
  if (category === 'JUMPING') return ['HEIGHT'];
  return [];
}

/** Reps of a descending ladder ("21-15-9", "10-9-8-7-6-5-4-3-2-1"), or null when the text is not one. */
export function ladderValues(text: string): number[] | null {
  const trimmed = text.trim();
  if (!/^\d+(\s*-\s*\d+)+$/.test(trimmed)) return null;
  return trimmed.split('-').map(v => Number(v.trim()));
}

/**
 * Sets of a strength or skill piece: "5x5" (5 sets of 5), "5-5-3-3-1" (5 sets, reps per set), or
 * null for free text ("build to a 1RM").
 */
export function parseSets(scheme: string): {sets: number; reps: string} | null {
  const text = scheme.trim();
  const setsByReps = /^(\d+)\s*[x×]\s*(\d+)$/i.exec(text);
  if (setsByReps) return {sets: Number(setsByReps[1]), reps: setsByReps[2]};
  const values = ladderValues(text);
  return values ? {sets: values.length, reps: values.join('-')} : null;
}

/** Reps of a scheme going one way only (21-15-9, 1-3-6-9): a For Time › Schéma; otherwise a pyramid (Ladder). */
export function monotone(values: number[]): boolean {
  const down = values.every((v, i) => i === 0 || v <= values[i - 1]);
  const up = values.every((v, i) => i === 0 || v >= values[i - 1]);
  return down || up;
}

/** Rounds written in a former rep scheme ("5 rounds", "3 Rounds for time"), or null. */
export function roundsOf(scheme: string | null | undefined): number | null {
  const match = /^(\d+)\s*rounds?\b/i.exec(scheme?.trim() ?? '');
  return match ? Number(match[1]) : null;
}
