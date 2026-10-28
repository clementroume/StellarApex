import {ChangeDetectionStrategy, Component, inject} from '@angular/core';
import {HlmToaster} from '@spartan-ng/helm/sonner';
import {ThemeService} from '../../services/theme.service';

/** Toaster for NotificationService (top centre: visible above the thumb on mobile), deferred to idle. */
@Component({
  selector: 'app-notification',
  standalone: true,
  imports: [HlmToaster],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @defer (on idle) {
      <hlm-toaster position="top-center" [theme]="themeService.currentTheme()"/>
    }
  `,
})
export class NotificationComponent {
  protected readonly themeService = inject(ThemeService);
}
