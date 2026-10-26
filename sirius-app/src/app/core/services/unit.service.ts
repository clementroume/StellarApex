import {computed, inject, Injectable, signal} from '@angular/core';
import {TranslateService} from '@ngx-translate/core';
import {AuthService} from '../../api/antares/services/auth.service';
import {UserService} from '../../api/antares/services/user.service';
import {UnitSystem} from '../../api/antares/models/user.model';

const STORAGE_KEY = 'unitSystem';
const LB_IN_KG = 0.45359237;

/**
 * Display unit for loads (kg or lb). Loads stay stored in the unit they were prescribed in; only the
 * display is converted. Without an explicit choice, the unit follows the language (French: kg).
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

  // --- ACTIONS & EVENTS ---
  toggle(): void {
    const next: UnitSystem = this.unitSystem() === 'METRIC' ? 'IMPERIAL' : 'METRIC';
    this.setUnitSystem(next);
  }

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

  /**
   * Formats a men's / women's load for display: "43/30 kg", "43 kg", or the source unit for
   * non-mass units.
   */
  formatLoad(weight?: number | null, weightFemale?: number | null, unit?: string | null): string {
    if (weight == null && weightFemale == null) return '';
    const isMass = !unit || unit === 'KG' || unit === 'LBS';
    const label = isMass ? (this.massUnit() === 'KG' ? 'kg' : 'lb') : (unit ?? '').toLowerCase();
    const values = [weight, weightFemale]
      .filter((v): v is number => v != null)
      .map(v => isMass ? this.convertLoad(v, unit) : v);
    return `${values.join('/')} ${label}`;
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
