import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { ThemeService } from '../../services/theme.service';
import { TranslateModule } from '@ngx-translate/core';

/**
 * The main navigation bar for the application.
 * It displays navigation links, the theme switcher, and a user menu for
 * authenticated users (or a login button for guests).
 * This component is designed for optimal performance with OnPush change detection.
 */
@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [CommonModule, RouterModule, TranslateModule],
  templateUrl: './navbar.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NavbarComponent {
  /**
   * Provides access to authentication state and methods.
   * Made public to be accessible from the component's template.
   */
  public readonly authService = inject(AuthService);

  /**
   * Provides access to theme management state and methods.
   * Made public to be accessible from the component's template.
   */
  public readonly themeService = inject(ThemeService);

  /**
   * Logs the user out by calling the AuthService's logout method and
   * subscribing to trigger the HTTP request.
   */
  logout(): void {
    this.authService.logout().subscribe();
  }
}
