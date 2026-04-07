import {effect, inject, Injectable, signal} from '@angular/core';
import {AuthService} from '../../api/antares/services/auth.service';
import {UserService} from '../../api/antares/services/user.service';
import {PreferencesUpdateRequest} from '../../api/antares/models/user.model';

export type Theme = 'light' | 'dark';

@Injectable({
  providedIn: 'root'
})
export class ThemeService {
  private readonly authService = inject(AuthService);
  private readonly userService = inject(UserService);

  private readonly _currentTheme = signal<Theme>(this.getInitialTheme());
  public readonly currentTheme = this._currentTheme.asReadonly();

  constructor() {
    effect(() => {
      const theme = this._currentTheme();
      localStorage.setItem('theme', theme);
      document.documentElement.dataset['theme'] = theme;
    });

    effect(() => {
      const user = this.authService.currentUser();
      if (user?.theme) {
        this._currentTheme.set(user.theme);
      }
    });
  }

  toggleTheme(): void {
    const newTheme = this._currentTheme() === 'light' ? 'dark' : 'light';
    this._currentTheme.set(newTheme);

    const currentUser = this.authService.currentUser();
    if (currentUser) {
      const preferences: PreferencesUpdateRequest = {
        locale: currentUser.locale as 'en' | 'fr',
        theme: newTheme,
      };

      this.userService.updatePreferences(preferences).subscribe({
        error: () => {
          console.error();
          // Revert to the original theme on failure to maintain consistency.
          this._currentTheme.set(currentUser.theme);
        }
      });
    }
  }

  private getInitialTheme(): Theme {
    return (localStorage.getItem('theme') as Theme) ?? 'dark';
  }
}
