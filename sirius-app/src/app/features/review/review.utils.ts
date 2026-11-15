import {MuscleBalance} from '../../api/aldebaran/models/balance.model';
import {addDays, shortDay} from '../sessions/session.utils';
import {zoneOf} from './muscle-zones';

/** The two tabs of the map (D88 bis): muscles producing the movement, or holding the position. */
export type MuscleTab = 'moving' | 'holding';

/** The 7 regions of the catalog, the region bars of the map. */
export const REGIONS = ['THIGH_LEG', 'HIP', 'BACK', 'SHOULDER', 'ARM_FOREARM', 'THORAX', 'ABDOMEN_PELVIS'] as const;

/** Sets of a muscle in a tab. */
export function setsOf(muscle: MuscleBalance, tab: MuscleTab): number {
  return tab === 'moving' ? muscle.movingSets : muscle.holdingSets;
}

/**
 * Shade of a value on the scale of its tab (validated mock-up v8): 0 = nothing, 1 to 5 = more and
 * more worked, 5 = the most worked of the tab. No number ever shows.
 */
export function level(value: number, max: number): number {
  if (value <= 0 || max <= 0) return 0;
  return Math.min(5, 1 + Math.floor((value / max) * 5));
}

/** Sum of a tab's sets per key (a zone of the drawing, or a region). */
export function sumBy(muscles: MuscleBalance[], tab: MuscleTab, key: (m: MuscleBalance) => string | null): Record<string, number> {
  const sums: Record<string, number> = {};
  for (const muscle of muscles) {
    const k = key(muscle);
    if (k) sums[k] = (sums[k] ?? 0) + setsOf(muscle, tab);
  }
  return sums;
}

/** Sets per zone of the drawing (D123). */
export function zoneSets(muscles: MuscleBalance[], tab: MuscleTab): Record<string, number> {
  return sumBy(muscles, tab, m => zoneOf(m.code));
}

/** The name of a muscle in the language shown: its common name, else its medical name. */
export function muscleName(muscle: MuscleBalance, lang: string): string {
  const common = lang.startsWith('fr') ? muscle.commonNameFr : muscle.commonNameEn;
  return common || muscle.medicalName;
}

/** "9 – 15 nov." / "Nov 9 – 15", the chosen week. */
export function weekLabel(monday: string, lang: string): string {
  return `${shortDay(monday, lang)} – ${shortDay(addDays(monday, 6), lang)}`;
}

/**
 * Names joined for a sentence: "Squat", "Squat et Clean", "Squat, Clean et Snatch".
 *
 * @param and The translated "and".
 */
export function joinNames(names: string[], and: string): string {
  if (names.length <= 1) return names.join('');
  return `${names.slice(0, -1).join(', ')} ${and} ${names[names.length - 1]}`;
}
