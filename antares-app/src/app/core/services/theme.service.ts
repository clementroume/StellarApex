import { effect, Injectable, signal, inject } from '@angular/core';
import { AuthService } from './auth.service';
import { PreferencesUpdateRequest } from '../models/user.model';

export type Theme = 'light' | 'dark';

/**
 * A singleton service for managing the application's visual theme.
 *
 * It persists the theme to localStorage for immediate application on startup and
 * synchronizes the theme with the authenticated user's preferences on the backend.
 */
@Injectable({
  providedIn: 'root'
})
export class ThemeService {
  private readonly authService = inject(AuthService);

  // Private writable signal for the current theme.
  private readonly _currentTheme = signal<Theme>(this.getInitialTheme());

  /** A public readonly signal representing the current theme. */
  public readonly currentTheme = this._currentTheme.asReadonly();

  constructor() {
    // Effect to apply the theme to the DOM and save it to localStorage whenever it changes.
    effect(() => {
      const theme = this._currentTheme();
      localStorage.setItem('theme', theme);
      document.documentElement.dataset['theme'] = theme;
    });

    // Effect to synchronize the theme from the user's profile upon login.
    effect(() => {
      const user = this.authService.currentUser();
      if (user?.theme) {
        this._currentTheme.set(user.theme);
      }
    });
  }

  /**
   * Toggles the current theme and persists the change to the backend if a user is authenticated.
   */
  toggleTheme(): void {
    const newTheme = this._currentTheme() === 'light' ? 'dark' : 'light';
    this._currentTheme.set(newTheme);

    const currentUser = this.authService.currentUser();
    if (currentUser) {
      const preferences: PreferencesUpdateRequest = {
        locale: currentUser.locale as 'en' | 'fr',
        theme: newTheme,
      };

      this.authService.updatePreferences(preferences).subscribe({
        error: (err) => {
          console.error('Failed to update theme preference:', err);
          // Revert to the original theme on failure to maintain consistency.
          this._currentTheme.set(currentUser.theme);
        }
      });
    }
  }

  /**
   * Retrieves the initial theme from localStorage, defaulting to 'light'.
   */
  private getInitialTheme(): Theme {
    return (localStorage.getItem('theme') as Theme) ?? 'light';
  }
}
