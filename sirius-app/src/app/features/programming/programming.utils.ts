import {DAYS_OF_WEEK, DayOfWeek, PatternAxis} from '../../api/aldebaran/models/programming.model';
import {MovementPattern, PATTERN_AXES} from '../../api/aldebaran/models/movement.model';

/** The radar axes in Aldebaran's order (MovementPattern.Axis). */
export const AXES: readonly PatternAxis[] = PATTERN_AXES.map(a => a.axis as PatternAxis);

/** The details of an axis shown on demand; none for Squat, Hinge and Lunge. */
export function detailsOf(axis: PatternAxis): readonly MovementPattern[] {
  const patterns = PATTERN_AXES.find(a => a.axis === axis)?.patterns ?? [];
  return patterns.length > 1 || axis === 'LOCOMOTION' || axis === 'CORE' ? patterns : [];
}

/** Outer ring of the radar, in percent: 30 at least, else the largest share rounded up to 10. */
export function radarScale(shares: number[]): number {
  return Math.max(30, Math.ceil(Math.max(0, ...shares) / 10) * 10);
}

/** A point of the radar: axis `index` of `count`, `value` on a scale of `max`, from the top clockwise. */
export function radarPoint(index: number, count: number, value: number, max: number, centre: number, radius: number): [number, number] {
  const angle = 2 * Math.PI * index / count;
  const r = radius * Math.min(value, max) / max;
  return [round(centre + r * Math.sin(angle)), round(centre - r * Math.cos(angle))];
}

/** "18,0 %" / "18.0 %". */
export function percent(value: number, lang: string): string {
  return `${value.toLocaleString(lang, {minimumFractionDigits: 1, maximumFractionDigits: 1})} %`;
}

/** "3,2" / "3.2". */
export function decimal(value: number, lang: string): string {
  return value.toLocaleString(lang, {maximumFractionDigits: 1});
}

/** "lun. · mer. · ven." / "Mon · Wed · Fri", in calendar order. */
export function daysLabel(days: readonly DayOfWeek[], lang: string): string {
  return sortDays(days).map(d => dayShort(d, lang)).join(' · ');
}

/** "lun." / "Mon": a day of the week in the language shown (a known Monday as reference). */
export function dayShort(day: DayOfWeek, lang: string): string {
  const date = new Date(2026, 10, 16 + DAYS_OF_WEEK.indexOf(day), 12);
  return date.toLocaleDateString(lang, {weekday: 'short'});
}

/** Days in calendar order, without duplicates. */
export function sortDays(days: readonly DayOfWeek[]): DayOfWeek[] {
  return DAYS_OF_WEEK.filter(d => days.includes(d));
}

function round(value: number): number {
  return Math.round(value * 100) / 100;
}
