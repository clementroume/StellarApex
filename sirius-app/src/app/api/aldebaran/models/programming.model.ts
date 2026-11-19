import {Modality, TimeDomain} from './balance.model';
import {MovementPattern} from './movement.model';

/** Day of the week as Java sends it. */
export type DayOfWeek = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';

/** Days in calendar order. */
export const DAYS_OF_WEEK: readonly DayOfWeek[] =
  ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];

/** An axis of the programming radar (MovementPattern.Axis). */
export type PatternAxis = 'SQUAT' | 'HINGE' | 'PUSH' | 'PULL' | 'LUNGE' | 'LOCOMOTION' | 'CORE';

/** One axis of the radar of a profile, against the full week. */
export interface AxisShare {
  axis: PatternAxis;
  share: number;
  fullShare: number;
  /** A gap on this axis raises an alert for this profile (above the floor and regular, D181). */
  judged: boolean;
}

/** One detail of an axis (Push V, Core Flexion...), against the full week. */
export interface DetailShare {
  pattern: MovementPattern;
  share: number;
  fullShare: number;
}

/** A region against the full week (information, D180). */
export interface RegionShare {
  region: string;
  share: number;
  fullShare: number;
  trend: 'BEHIND' | 'IN_LINE' | 'AHEAD';
}

/** An alert: a pattern gap or a group under its minimum (D180). */
export interface ProgrammingAlert {
  kind: 'PATTERN_GAP' | 'GROUP_MINIMUM';
  /** The axis, or the group's muscle code. */
  subject: string;
  /** Share of the axis (percent), or sets per week. */
  value: number;
  /** 0.6 x the full-week share, or 4 sets per week. */
  limit: number;
}

/** The balance of one profile (the full week included) over its window shown. */
export interface ProgrammingProfile {
  days: DayOfWeek[];
  windowStart: string;
  windowEnd: string;
  inAlert: boolean;
  axes: AxisShare[];
  details: DetailShare[];
  regions: RegionShare[];
  alerts: ProgrammingAlert[];
  minutesPerWeek: Record<TimeDomain, number>;
  modalities: Record<Modality, number>;
}

/** A profile in alert, with its main alert. */
export interface ProfileRow {
  days: DayOfWeek[];
  main: ProgrammingAlert;
  alerts: number;
}

/** The profiles of one size (3, 4 or 5 days); `total` 0 when the size is the full week or more. */
export interface ProfileSummary {
  size: number;
  total: number;
  inAlert: ProfileRow[];
}

/** A session whose region is unusual against the 4 weeks before (information, D173). */
export interface UnusualSession {
  date: string;
  region: string;
  sets: number;
  base: number;
}

/**
 * The balance of a gym's programming over a cycle (ProgrammingBalanceResponse, lot D'): staff only,
 * never shown to an athlete (D89).
 */
export interface ProgrammingBalance {
  gymId: number;
  start: string;
  end: string;
  weeks: number;
  scaled: boolean;
  optional: boolean;
  trainingDays: DayOfWeek[];
  sessions: number;
  windows: number;
  uncountedSessions: number;
  uncountedLines: number;
  /** Weeks of the cycle holding a counted session (fewer than `weeks`: empty weeks are read). */
  programmedWeeks: number;
  /** Lines with an official Scaled version (0: Scaled reads as RX). */
  scaledLines: number;
  fullWeek: ProgrammingProfile;
  summaries: ProfileSummary[];
  profile: ProgrammingProfile | null;
  /** Axes an athlete without fixed days often misses (information, D181). */
  concentratedAxes: PatternAxis[];
  unusualSessions: UnusualSession[];
  capabilities: {canReorder: boolean};
}

/** What the coach asks: the cycle, the switches and the profile to detail. */
export interface ProgrammingQuery {
  /** Monday of the first week (yyyy-MM-dd). */
  start: string;
  weeks: number;
  scaled: boolean;
  optional: boolean;
  days: DayOfWeek[];
}
