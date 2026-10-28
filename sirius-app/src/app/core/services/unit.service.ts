import {computed, inject, Injectable, signal} from '@angular/core';
import {TranslateService} from '@ngx-translate/core';
import {AuthService} from '../../api/antares/services/auth.service';
import {UserService} from '../../api/antares/services/user.service';
import {UnitSystem} from '../../api/antares/models/user.model';

const STORAGE_KEY = 'unitSystem';
const LB_IN_KG = 0.45359237;

/** Size of each distance unit in meters (Aldebaran's Unit enum). */
const METERS_PER: Record<string, number> = {
  METERS: 1, KILOMETERS: 1000, CENTIMETERS: 0.01, INCHES: 0.0254, FEET: 0.3048, YARDS: 0.9144, MILES: 1609.344
};
const LABELS: Record<string, string> = {
  KG: 'kg', LBS: 'lb', METERS: 'm', KILOMETERS: 'km', CENTIMETERS: 'cm', INCHES: 'in', FEET: 'ft', YARDS: 'yd',
  MILES: 'mi', SECONDS: 's', MINUTES: 'min'
};

/** A value converted for display, with the unit it is now expressed in. */
export interface DisplayValue {
  values: number[];
  unit: string;
}

/** Men's/women's values ready to print, `mine` = index of the user's division value (or null). */
export interface MeasurePair {
  values: string[];
  unit: string;
  mine: number | null;
}

/**
 * Display units (metric or imperial) for loads, distances and heights. Values stay stored in the unit
 * they were prescribed in; only the display is converted. Without an explicit choice, the system
 * follows the language (French: metric).
 *
 * Distances follow CrossFit usage: imperial keeps runs and rows in meters (400 m, 2 km) and only
 * converts short distances (under 100 m) to feet; metric converts every imperial distance.
 */
@Injectable({
  providedIn: 'root'
})
export class UnitService {
  // --- INJECTIONS ---
  private readonly authService = inject(AuthService);
  private readonly userService = inject(UserService);
  private readonly translate = inject(TranslateService);

  // --- STATE (SIGNALS & VARIABLES) ---
  // Choice made on this screen (kept during a support session, where Antares refuses to save it)
  private readonly localChoice = signal<UnitSystem | null>(this.readStoredChoice());

  // --- COMPUTED STATE ---
  readonly unitSystem = computed<UnitSystem>(() => {
    const user = this.authService.currentUser();
    const saved = user && !user.supportSession ? user.unitSystem : null;
    const lang = this.translate.currentLang() ?? this.translate.fallbackLang() ?? 'en';
    return saved ?? this.localChoice() ?? (lang === 'fr' ? 'METRIC' : 'IMPERIAL');
  });

  /** Unit code used by Aldebaran for the current display unit. */
  readonly massUnit = computed(() => this.unitSystem() === 'METRIC' ? 'KG' : 'LBS');

  /** Unit label of heights typed by the user: "cm" or "in". */
  readonly heightUnit = computed(() => this.unitSystem() === 'METRIC' ? 'cm' : 'in');

  // --- ACTIONS & EVENTS ---
  setUnitSystem(unitSystem: UnitSystem): void {
    this.localChoice.set(unitSystem);
    try {
      localStorage.setItem(STORAGE_KEY, unitSystem);
    } catch {
      // Storage unavailable (private mode): the choice lasts for this page only
    }

    const user = this.authService.currentUser();
    if (user && !user.supportSession) {
      this.userService.updatePreferences({locale: user.locale, theme: user.theme, unitSystem}).subscribe({
        error: () => this.localChoice.set(user.unitSystem ?? null)
      });
    }
  }

  // --- HELPERS ---
  /**
   * Converts a load to the display unit, rounded to the nearest unit (as gyms write it: 95 lb = 43 kg).
   *
   * @param value The load, in `unit`.
   * @param unit The unit of the load (KG or LBS); other units are returned unchanged.
   */
  convertLoad(value: number, unit?: string | null): number {
    const target = this.massUnit();
    if (!unit || unit === target || (unit !== 'KG' && unit !== 'LBS')) return Number(value.toFixed(1));
    const converted = unit === 'LBS' ? value * LB_IN_KG : value / LB_IN_KG;
    return Math.round(converted);
  }

  /** Men's / women's load in the display unit: "43/30 kg", "43 kg". */
  formatLoad(weight?: number | null, weightFemale?: number | null, unit?: string | null): string {
    if (weight == null && weightFemale == null) return '';
    const values = [weight, weightFemale].filter((v): v is number => v != null).map(v => this.convertLoad(v, unit));
    return this.format({values, unit: this.massUnit()});
  }

  /**
   * Converts a lifted load (a score) to the display unit. Unlike prescriptions, scores keep their
   * precision: 0.5 kg, 1 lb (102.5 kg stays 102.5 kg; 225 lb -> 102 kg).
   *
   * @param value The load, in `unit`.
   * @param unit The unit of the load (KG or LBS).
   */
  convertScoreLoad(value: number, unit?: string | null): number {
    const target = this.massUnit();
    if (!unit || unit === target || (unit !== 'KG' && unit !== 'LBS')) return Number(value.toFixed(1));
    const converted = unit === 'LBS' ? value * LB_IN_KG : value / LB_IN_KG;
    return target === 'KG' ? Math.round(converted * 2) / 2 : Math.round(converted);
  }

  /** A lifted load (score) in the display unit: "102.5 kg", "225 lb". */
  formatScoreLoad(value?: number | null, unit?: string | null): string {
    if (value == null) return '';
    return this.format({values: [this.convertScoreLoad(value, unit)], unit: this.massUnit()});
  }

  /**
   * Converts distances (men's then women's value) to the display system, e.g. 200 ft -> "61 m",
   * 1 mi -> "1.6 km", 60/50 cm -> "24/20 in". Units unknown to the conversion are kept.
   */
  convertDistance(values: number[], unit?: string | null): DisplayValue {
    if (!unit || !(unit in METERS_PER)) return {values, unit: unit ?? ''};
    const meters = values.map(v => v * METERS_PER[unit]);
    const target = this.distanceTarget(unit, Math.max(...meters));
    if (target === unit) return {values, unit};
    return {values: meters.map(m => this.roundDistance(m / METERS_PER[target], target)), unit: target};
  }

  /** Men's / women's distance or height in the display system: "60/50 cm", "400 m". */
  formatDistance(value?: number | null, valueFemale?: number | null, unit?: string | null): string {
    const values = [value, valueFemale].filter((v): v is number => v != null);
    return values.length ? this.format(this.convertDistance(values, unit)) : '';
  }

  /** A body weight (stored in kg) in the display unit, 0.1 precision: 78.5 kg, 173.1 lb. */
  bodyWeightFromKg(kg: number): number {
    return Number((this.massUnit() === 'KG' ? kg : kg / LB_IN_KG).toFixed(1));
  }

  /** A body weight typed in the display unit, back to kilograms (0.01 kg, as Antares stores it). */
  bodyWeightToKg(value: number): number {
    return Number((this.massUnit() === 'KG' ? value : value * LB_IN_KG).toFixed(2));
  }

  /** "78.5 kg", "173.1 lb". */
  formatBodyWeight(kg?: number | null): string {
    return kg == null ? '' : this.format({values: [this.bodyWeightFromKg(kg)], unit: this.massUnit()});
  }

  /** A height (stored in cm) in the display unit: centimetres, or whole inches in imperial. */
  heightFromCm(cm: number): number {
    return this.unitSystem() === 'METRIC' ? cm : Math.round(cm / 2.54);
  }

  /** A height typed in the display unit, back to whole centimetres. */
  heightToCm(value: number): number {
    return this.unitSystem() === 'METRIC' ? Math.round(value) : Math.round(value * 2.54);
  }

  /**
   * A men/women pair in the display unit, with the user's own value marked: Sirius highlights the
   * load of the user's division (D31). `mine` is null without a division or without a pair.
   */
  pair(values: DisplayValue, division?: 'MEN' | 'WOMEN' | null): MeasurePair {
    const lang = this.translate.currentLang() ?? this.translate.fallbackLang() ?? 'en';
    const texts = values.values.map(v => v.toLocaleString(lang, {maximumFractionDigits: 2, useGrouping: false}));
    const mine = texts.length === 2 && division ? (division === 'MEN' ? 0 : 1) : null;
    return {values: texts, unit: LABELS[values.unit] ?? values.unit.toLowerCase(), mine};
  }

  /** Men's / women's load as a pair (see `pair`), or null without a load. */
  loadPair(weight?: number | null, weightFemale?: number | null, unit?: string | null,
           division?: 'MEN' | 'WOMEN' | null): MeasurePair | null {
    if (weight == null && weightFemale == null) return null;
    if (weight == null || weightFemale == null) {
      return this.pair({values: [this.convertLoad((weight ?? weightFemale)!, unit)], unit: this.massUnit()});
    }
    return this.pair({values: [weight, weightFemale].map(v => this.convertLoad(v, unit)), unit: this.massUnit()},
      division);
  }

  /** Men's / women's height (box, target) as a pair (see `pair`), or null without a height. */
  distancePair(value?: number | null, valueFemale?: number | null, unit?: string | null,
               division?: 'MEN' | 'WOMEN' | null): MeasurePair | null {
    const values = [value, valueFemale].filter((v): v is number => v != null);
    if (values.length === 0) return null;
    return this.pair(this.convertDistance(values, unit), values.length === 2 ? division : null);
  }

  /** "43/30 kg": values joined with a slash, then the unit label; decimals as the language writes them ("1,6 km"). */
  format(value: DisplayValue): string {
    const lang = this.translate.currentLang() ?? this.translate.fallbackLang() ?? 'en';
    const numbers = value.values.map(v => v.toLocaleString(lang, {maximumFractionDigits: 2, useGrouping: false}));
    return `${numbers.join('/')} ${LABELS[value.unit] ?? value.unit.toLowerCase()}`.trim();
  }

  private distanceTarget(unit: string, maxMeters: number): string {
    const metric = this.unitSystem() === 'METRIC';
    const isMetricUnit = unit === 'METERS' || unit === 'KILOMETERS' || unit === 'CENTIMETERS';
    if (metric) {
      if (isMetricUnit) return unit;
      if (unit === 'INCHES') return 'CENTIMETERS';
      return unit === 'MILES' ? 'KILOMETERS' : 'METERS';
    }
    if (!isMetricUnit) return unit;
    if (unit === 'CENTIMETERS') return 'INCHES';
    // Runs and rows stay in meters in CrossFit, even in imperial
    return unit === 'METERS' && maxMeters < 100 ? 'FEET' : unit;
  }

  /** Rounds a converted distance as gyms write it: 24/20 in -> 60/50 cm, 10/9 ft -> 3/2.7 m. */
  private roundDistance(value: number, unit: string): number {
    if (unit === 'CENTIMETERS') return Math.round(value / 5) * 5;
    if (unit === 'KILOMETERS' || unit === 'MILES' || (unit === 'METERS' && value < 10)) {
      return Number(value.toFixed(1));
    }
    return Math.round(value);
  }

  private readStoredChoice(): UnitSystem | null {
    try {
      const value = localStorage.getItem(STORAGE_KEY);
      return value === 'METRIC' || value === 'IMPERIAL' ? value : null;
    } catch {
      return null;
    }
  }
}
