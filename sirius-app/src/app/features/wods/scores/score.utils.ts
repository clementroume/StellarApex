import {ScalingLevel, ScoreResponse} from '../../../api/aldebaran/models/score.model';
import {UnitService} from '../../../core/services/unit.service';
import {formatDuration} from '../wod.utils';

/** Translation function (TranslateService.instant). */
export type Translate = (key: string, params?: object) => string;

/** Levels offered when logging a score, from the most demanding. */
export const SCALING_LEVELS: ScalingLevel[] = ['ELITE', 'RX', 'SCALED', 'CUSTOM'];

/**
 * The performance of a score for the WOD's score type, as athletes write it: "4:32", "CAP + 45 reps",
 * "12 rounds + 5 reps", "24 rounds" (EMOM), "102.5 kg" (in the user's unit), "86 cal", "5 km", "Done".
 */
export function formatScore(score: ScoreResponse, scoreType: string, units: UnitService, t: Translate): string {
  switch (scoreType) {
    case 'TIME':
      return score.timeCapped
        ? t('SCORE.DISPLAY.CAPPED', {reps: score.reps ?? 0})
        : formatDuration(score.timeSeconds);
    case 'ROUNDS_REPS':
      return score.reps
        ? t('SCORE.DISPLAY.ROUNDS_REPS', {rounds: score.rounds ?? 0, reps: score.reps})
        : t('SCORE.DISPLAY.ROUNDS', {rounds: score.rounds ?? 0});
    case 'ROUNDS':
      return t('SCORE.DISPLAY.ROUNDS', {rounds: score.rounds ?? 0});
    case 'REPS':
      return t('SCORE.DISPLAY.REPS', {reps: score.reps ?? 0});
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
