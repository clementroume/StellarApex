import {ChangeDetectionStrategy, Component, effect, inject} from '@angular/core';
import {RouterOutlet} from '@angular/router';
import {TranslateService} from '@ngx-translate/core';
import {AuthService} from './api/antares/services/auth.service';
import {GlobalLoaderComponent} from './core/layout/global-loader/global-loader.component';
import {NotificationComponent} from './core/layout/notification/notification.component';
import {AppUpdateService} from './core/services/app-update.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, GlobalLoaderComponent, NotificationComponent],
  templateUrl: './app.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AppComponent {
  private readonly translate = inject(TranslateService);
  private readonly authService = inject(AuthService);

  constructor() {
    inject(AppUpdateService).watch();
    this.translate.setFallbackLang('en');
    const browserLang = this.translate.getBrowserLang() ?? 'en';
    this.translate.use(new RegExp(/fr/).exec(browserLang) ? 'fr' : 'en');

    effect(() => {
      const user = this.authService.currentUser();
      // During a support session the screen keeps the admin's language, not the target's
      if (user?.locale && !user.supportSession) {
        this.translate.use(user.locale);
      }
    });
  }
}
