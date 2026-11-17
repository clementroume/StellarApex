import {CardioEquivalent} from '../../../../api/aldebaran/models/score.model';
import {WodMovementResponse, WodResponse} from '../../../../api/aldebaran/models/wod.model';

/** Lines of a WOD in the order it is written. */
export function sortedLines(wod: Pick<WodResponse, 'movements'> | null | undefined): WodMovementResponse[] {
  return [...(wod?.movements ?? [])].sort((a, b) => a.orderIndex - b.orderIndex);
}

/** A line prescribed as "max" reps. */
export function isMaxLine(line: Pick<WodMovementResponse, 'repsScheme'>): boolean {
  return (line.repsScheme ?? '').trim().toLowerCase() === 'max';
}

/**
 * Lines whose reps the athlete counts (D147): the "max" lines of a WOD scored in reps that has several (Lynne:
 * max bench press and max pull-ups, score = total).
 */
export function countedLines(wod: WodResponse | null | undefined): WodMovementResponse[] {
  if (!wod || wod.scoreType !== 'REPS') return [];
  const max = sortedLines(wod).filter(isMaxLine);
  return max.length >= 2 ? max : [];
}

/**
 * Lines strength sets are entered on (D143): every line, a complex once (its first line, a set = a round).
 */
export function setLines(wod: WodResponse | null | undefined): WodMovementResponse[] {
  const seen = new Set<number>();
  return sortedLines(wod).filter(line => {
    if (line.complexGroup == null) return true;
    if (seen.has(line.complexGroup)) return false;
    seen.add(line.complexGroup);
    return true;
  });
}

/** Whether a WOD has an official Scaled version (D141). */
export function hasOfficialScaled(wod: WodResponse | null | undefined): boolean {
  return !!wod?.movements?.some(line => !!line.scaled);
}

/** The cardio values of a movement or of one of its usual names (D151), or null. */
function cardioOf(table: CardioEquivalent[], movementName: string, usualName?: string | null): CardioEquivalent | null {
  return table.find(e => e.movementName === movementName && usualName && e.usualNameEn === usualName)
    ?? table.find(e => e.movementName === movementName && !e.usualNameEn)
    ?? null;
}

/**
 * The quantity proposed when a cardio line is done on another machine (D151, box tables): Run 400 m done on the
 * rower = 500 m, or 40 cal when the rower is counted in calories here. Null when either movement is not cardio.
 *
 * @param line The prescribed line (distance in its unit, or calories).
 * @param from The prescribed movement and usual name.
 * @param to The movement and usual name done.
 * @param table The cardio table (score reference data).
 * @param women Whether the women's calories apply.
 */
export function cardioQuantity(
  line: Pick<WodMovementResponse, 'distance' | 'distanceUnit' | 'calories' | 'caloriesFemale'>,
  from: {movementName: string; usualName?: string | null},
  to: {movementName: string; usualName?: string | null},
  table: CardioEquivalent[],
  women: boolean
): {distance?: number; distanceUnit?: string; calories?: number} | null {
  const a = cardioOf(table, from.movementName, from.usualName);
  const b = cardioOf(table, to.movementName, to.usualName);
  if (!a || !b || (a === b)) return null;
  const perMeter = (e: CardioEquivalent) => (women && e.caloriesFemale != null ? e.caloriesFemale : e.calories) ?? null;
  // Meters of running the line is worth
  let runMeters: number | null = null;
  if (line.distance != null && a.meters) {
    runMeters = toMeters(line.distance, line.distanceUnit) / a.meters;
  } else {
    const cal = women && line.caloriesFemale != null ? line.caloriesFemale : line.calories;
    const rate = perMeter(a);
    if (cal != null && rate) runMeters = cal / rate;
  }
  if (runMeters == null) return null;
  // Calories when the line was in calories and the machine counts them, meters otherwise
  const rate = perMeter(b);
  if (line.distance == null && rate) return {calories: Math.round(runMeters * rate)};
  if (b.meters) return {distance: Math.round(runMeters * b.meters), distanceUnit: 'METERS'};
  return rate ? {calories: Math.round(runMeters * rate)} : null;
}

const METERS_PER: Record<string, number> = {
  METERS: 1, KILOMETERS: 1000, CENTIMETERS: 0.01, INCHES: 0.0254, FEET: 0.3048, YARDS: 0.9144, MILES: 1609.344
};

function toMeters(value: number, unit?: string | null): number {
  return value * (METERS_PER[unit ?? 'METERS'] ?? 1);
}
