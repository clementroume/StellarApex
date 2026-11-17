import {ScalingLevel, ScoreLineResponse, ScoreResponse} from '../../../api/aldebaran/models/score.model';
import {UnitService} from '../../../core/services/unit.service';
import {formatDuration} from '../wod.utils';

/** What a score needs to know about its WOD to be written. */
export interface ScoredWod {
  scoreType: string;
  repsPerRound?: number | null;
}

/** Total reps as rounds + reps of a fixed round (312 reps of 30 -> 10 rounds + 12), or null. */
export function splitRounds(total?: number | null, repsPerRound?: number | null): {rounds: number; reps: number} | null {
  if (total == null || !repsPerRound) return null;
  return {rounds: Math.floor(total / repsPerRound), reps: total % repsPerRound};
}

/** Translation function (TranslateService.instant). */
export type Translate = (key: string, params?: object) => string;

/** Levels of a score (lot S, D140): RX = nothing changed, SCALED = changes detailed per line. */
export const SCALING_LEVELS: ScalingLevel[] = ['RX', 'SCALED'];

/**
 * Translation key of a score's level as shown: Rx, official Scaled (ranked), Rx+ (a Scaled every line of which is
 * at least RX, never ranked) or Scaled.
 */
export function levelKey(score: Pick<ScoreResponse, 'scaling' | 'scaledOfficial' | 'rxPlus'>): string {
  if (score.scaling === 'RX') return 'SCORE.SCALING.RX';
  if (score.scaledOfficial) return 'SCORE.SCALING.OFFICIAL';
  return score.rxPlus ? 'SCORE.SCALING.RX_PLUS' : 'SCORE.SCALING.SCALED';
}

/** Whether a score has a rank (RX, or the official Scaled version; a free Scaled never, D141). */
export function isRanked(score: Pick<ScoreResponse, 'scaling' | 'scaledOfficial'>): boolean {
  return score.scaling === 'RX' || !!score.scaledOfficial;
}

/** A line done otherwise than prescribed, as written: "Ring Row · 21-15-9 · 30 kg" (CrossFit English names). */
export function formatScoreLine(line: ScoreLineResponse, weightUnit: string | null | undefined, units: UnitService): string {
  const name = line.display?.usualNameEn || line.movement?.name || '';
  const parts = [name];
  if (line.repsScheme) parts.push(line.repsScheme);
  if (line.weight != null) parts.push(units.formatScoreLoad(line.weight, weightUnit));
  if (line.distanceMeters != null) parts.push(units.formatDistance(line.distanceMeters, null, 'METERS'));
  if (line.calories != null) parts.push(`${line.calories} cal`);
  if (line.heightMeters != null) parts.push(units.formatDistance(line.heightMeters, null, 'METERS'));
  if (line.durationSeconds != null) parts.push(formatDuration(line.durationSeconds));
  return parts.filter(Boolean).join(' · ');
}

/**
 * The performance of a score for the WOD's score type, as athletes write it: "4:32", "CAP + 45 reps",
 * "312 reps · 10 rounds + 12 reps" (AMRAP with a fixed round, stored as total reps), "24 rounds" (EMOM), "102.5 kg" (in the user's unit), "86 cal", "5 km", "Done".
 */
export function formatScore(score: ScoreResponse, wod: ScoredWod, units: UnitService, t: Translate): string {
  switch (wod.scoreType) {
    case 'TIME':
      return score.timeCapped
        ? t('SCORE.DISPLAY.CAPPED', {reps: score.reps ?? 0})
        : formatDuration(score.timeSeconds);
    case 'ROUNDS':
      return t('SCORE.DISPLAY.ROUNDS', {rounds: score.rounds ?? 0});
    case 'REPS': {
      const total = t('SCORE.DISPLAY.REPS', {reps: score.reps ?? 0});
      // The round as done: reduced reps make a shorter round (D147)
      const split = splitRounds(score.reps, score.roundReps ?? wod.repsPerRound);
      if (!split) return total;
      const rounds = split.reps
        ? t('SCORE.DISPLAY.ROUNDS_REPS', {rounds: split.rounds, reps: split.reps})
        : t('SCORE.DISPLAY.ROUNDS', {rounds: split.rounds});
      return `${total} · ${rounds}`;
    }
    case 'WEIGHT':
      return units.formatScoreLoad(score.maxWeight, score.weightUnit);
    case 'LOAD':
      return units.formatScoreLoad(score.totalLoad, score.weightUnit);
    case 'CALORIES':
      return score.totalCalories != null ? `${score.totalCalories} cal` : '';
    case 'DISTANCE':
      return units.formatDistance(score.totalDistance, null, score.distanceUnit);
    default:
      return t('SCORE.DISPLAY.DONE');
  }
}
