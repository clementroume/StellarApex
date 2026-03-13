import {ChangeDetectionStrategy, Component, inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {RouterModule} from '@angular/router';
import {AuthService} from '../../../api/antares/services/auth.service';
import {ThemeService} from '../../services/theme.service';
import {TranslateModule} from '@ngx-translate/core';
import {NgIcon} from '@ng-icons/core';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [CommonModule, RouterModule, TranslateModule, NgIcon],
  templateUrl: './navbar.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NavbarComponent {
  public readonly authService = inject(AuthService);
  public readonly themeService = inject(ThemeService);

  logout(): void {
    this.authService.logout().subscribe();
  }
}
