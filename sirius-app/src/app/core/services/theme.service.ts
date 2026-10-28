import {effect, inject, Injectable, signal} from '@angular/core';
import {AuthService} from '../../api/antares/services/auth.service';
import {UserService} from '../../api/antares/services/user.service';
import {Palette, PALETTES, PreferencesUpdateRequest, Theme} from '../../api/antares/models/user.model';

export type {Theme} from '../../api/antares/models/user.model';

/**
 * Light/dark theme and colour palette, applied as `data-theme` / `data-palette` on <html>
 * (see palettes.css) and saved in the user's Antares preferences.
 */
@Injectable({
  providedIn: 'root'
})
export class ThemeService {
  // --- INJECTIONS ---
  private readonly authService = inject(AuthService);
  private readonly userService = inject(UserService);

  // --- STATE (SIGNALS & VARIABLES) ---
  private readonly _currentTheme = signal<Theme>(this.readStored('theme', ['light', 'dark'], 'dark'));
  public readonly currentTheme = this._currentTheme.asReadonly();
  private readonly _currentPalette = signal<Palette>(this.readStored('palette', PALETTES, 'ocean'));
  public readonly currentPalette = this._currentPalette.asReadonly();

  // --- LIFECYCLE ---
  constructor() {
    effect(() => {
      const theme = this._currentTheme();
      this.store('theme', theme);
      document.documentElement.dataset['theme'] = theme;
    });

    effect(() => {
      const palette = this._currentPalette();
      this.store('palette', palette);
      document.documentElement.dataset['palette'] = palette;
    });

    effect(() => {
      const user = this.authService.currentUser();
      // During a support session the screen keeps the admin's theme and palette, not the target's
      if (user && !user.supportSession) {
        if (user.theme) this._currentTheme.set(user.theme);
        if (user.palette) this._currentPalette.set(user.palette);
      }
    });
  }

  // --- ACTIONS & EVENTS ---
  toggleTheme(): void {
    const previous = this._currentTheme();
    const newTheme: Theme = previous === 'light' ? 'dark' : 'light';
    this._currentTheme.set(newTheme);
    this.save({theme: newTheme}, () => this._currentTheme.set(previous));
  }

  setPalette(palette: Palette): void {
    const previous = this._currentPalette();
    if (palette === previous) return;
    this._currentPalette.set(palette);
    this.save({palette}, () => this._currentPalette.set(previous));
  }

  // --- HELPERS ---
  /** Saves the change; during a support session it stays on the admin's screen (Antares refuses it). */
  private save(change: Partial<PreferencesUpdateRequest>, revert: () => void): void {
    const user = this.authService.currentUser();
    if (!user || user.supportSession) return;
    this.userService.updatePreferences({
      locale: user.locale,
      theme: user.theme,
      // Sent back unchanged: the preferences are replaced as a whole (an omitted palette is kept)
      unitSystem: user.unitSystem ?? null,
      ...change
    }).subscribe({error: revert});
  }

  private readStored<T extends string>(key: string, allowed: readonly T[], fallback: T): T {
    try {
      const value = localStorage.getItem(key) as T | null;
      return value && allowed.includes(value) ? value : fallback;
    } catch {
      return fallback;
    }
  }

  private store(key: string, value: string): void {
    try {
      localStorage.setItem(key, value);
    } catch {
      // Storage unavailable (private mode): the choice still applies for this visit
    }
  }
}
