import {ProgrammingBalance, ProgrammingProfile} from '../../api/aldebaran/models/programming.model';

/** A profile of the test programming: Squat at `squat` % against 36 % in the full week. */
export function profileOf(squat: number, alerts: ProgrammingProfile['alerts'] = []): ProgrammingProfile {
  return {
    days: ['MONDAY', 'WEDNESDAY', 'FRIDAY'],
    windowStart: '2026-11-16',
    windowEnd: '2026-12-13',
    inAlert: alerts.length > 0,
    axes: [
      {axis: 'SQUAT', share: squat, fullShare: 36, judged: true},
      {axis: 'PULL', share: 30, fullShare: 28, judged: true},
      {axis: 'CORE', share: 0, fullShare: 12, judged: false}
    ],
    details: [{pattern: 'CORE_FLEXION', share: 0, fullShare: 12}, {pattern: 'PULL_V', share: 30, fullShare: 28}],
    regions: [
      {region: 'THIGH_LEG', share: 30, fullShare: 40, trend: 'BEHIND'},
      {region: 'SHOULDER', share: 13, fullShare: 10, trend: 'AHEAD'}
    ],
    alerts,
    minutesPerWeek: {VERY_SHORT: 0, SHORT: 12, MEDIUM: 20, LONG: 0, INTERVALS: 0},
    modalities: {GYMNASTICS: 40, WEIGHTLIFTING: 40, MONOSTRUCTURAL: 20}
  };
}

/** The test programming: Monday to Saturday, one 3-day profile in alert on Squat. */
export function balanceOf(withProfile = false): ProgrammingBalance {
  return {
    gymId: 4, start: '2026-11-16', end: '2026-12-13', weeks: 4, scaled: false, optional: false,
    trainingDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'],
    sessions: 24, windows: 1, uncountedSessions: 2, uncountedLines: 0, programmedWeeks: 4, scaledLines: 3,
    fullWeek: {...profileOf(36), days: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY']},
    summaries: [
      {size: 3, total: 20, inAlert: [{days: ['THURSDAY', 'FRIDAY', 'SATURDAY'], alerts: 1,
        main: {kind: 'PATTERN_GAP', subject: 'SQUAT', value: 0, limit: 21.6}}]},
      {size: 4, total: 15, inAlert: []}
    ],
    profile: withProfile
      ? profileOf(0, [{kind: 'PATTERN_GAP', subject: 'SQUAT', value: 0, limit: 21.6}])
      : null,
    concentratedAxes: ['CORE'],
    unusualSessions: [{date: '2026-12-05', region: 'SHOULDER', sets: 34, base: 26}],
    capabilities: {canReorder: false}
  };
}
