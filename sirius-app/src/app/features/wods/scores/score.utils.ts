import {ScalingLevel, ScoreResponse} from '../../../api/aldebaran/models/score.model';
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

/** Levels offered when logging a score, from the most demanding. */
export const SCALING_LEVELS: ScalingLevel[] = ['ELITE', 'RX', 'SCALED', 'CUSTOM'];

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
      const split = splitRounds(score.reps, wod.repsPerRound);
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
