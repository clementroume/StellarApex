import {ChangeDetectionStrategy, Component, effect, inject} from '@angular/core';
import {RouterOutlet} from '@angular/router';
import {TranslateService} from '@ngx-translate/core';
import {AuthService} from './api/antares/services/auth.service';
import {GlobalLoaderComponent} from './core/layout/global-loader/global-loader.component';
import {NotificationComponent} from './core/layout/notification/notification.component';

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
    this.translate.setFallbackLang('en');
    const browserLang = this.translate.getBrowserLang() ?? 'en';
    this.translate.use(new RegExp(/fr/).exec(browserLang) ? 'fr' : 'en');

    effect(() => {
      const user = this.authService.currentUser();
      if (user?.locale) {
        this.translate.use(user.locale);
      }
    });
  }
}
