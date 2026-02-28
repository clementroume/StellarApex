import {ChangeDetectionStrategy, Component, effect, inject} from '@angular/core';
import {RouterOutlet} from '@angular/router';
import {TranslateService} from '@ngx-translate/core';
import {AuthService} from './api/antares/services/auth.service';
import {GlobalLoaderComponent} from './core/layout/global-loader/global-loader.component';
import {NotificationComponent} from './core/layout/notification/notification.component';

/**
 * The root component of the application.
 * It serves as the main application shell and contains the primary router outlet.
 *
 * This component is responsible for initializing the translation service and reacting
 * to user authentication changes to set the appropriate language.
 */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, GlobalLoaderComponent, NotificationComponent],
  templateUrl: './app.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush, // Best practice for performance
})
export class AppComponent {
  private readonly translate = inject(TranslateService);
  private readonly authService = inject(AuthService);

  constructor() {
    // Set a default language and use the browser's language on initial load.
    this.translate.setFallbackLang('en');
    const browserLang = this.translate.getBrowserLang() ?? 'en';
    this.translate.use(new RegExp(/fr/).exec(browserLang) ? 'fr' : 'en');

    // An effect that reacts to user state changes.
    // When a user logs in, it sets the application language to their preference.
    effect(() => {
      const user = this.authService.currentUser();
      if (user?.locale) {
        this.translate.use(user.locale);
      }
    });
  }
}
