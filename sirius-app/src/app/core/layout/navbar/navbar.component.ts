import {ChangeDetectionStrategy, Component, inject} from '@angular/core';
import {RouterLink, RouterLinkActive} from '@angular/router';
import {TranslatePipe} from '@ngx-translate/core';
import {NgIcon} from '@ng-icons/core';
import {HlmButton} from '@spartan-ng/helm/button';
import {AuthService} from '../../../api/antares/services/auth.service';
import {ThemeService} from '../../services/theme.service';
import {DisplayGymService} from '../../services/display-gym.service';
import {SpacesService} from '../spaces.service';
import {GymSelectorComponent} from '../../../shared/component/gym-selector/gym-selector.component';

/** Top bar: spaces (desktop), displayed box/prog, theme, account and logout. */
@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, TranslatePipe, NgIcon, HlmButton, GymSelectorComponent],
  templateUrl: './navbar.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NavbarComponent {
  // --- INJECTIONS ---
  private readonly authService = inject(AuthService);
  protected readonly themeService = inject(ThemeService);
  protected readonly displayGym = inject(DisplayGymService);
  protected readonly spacesService = inject(SpacesService);

  // --- ACTIONS & EVENTS ---
  logout(): void {
    this.authService.logout().subscribe();
  }
}
