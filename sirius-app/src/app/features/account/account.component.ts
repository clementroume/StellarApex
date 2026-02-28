import {ChangeDetectionStrategy, Component} from '@angular/core';
import {RouterModule} from '@angular/router';
import {NavbarComponent} from '../../core/layout/navbar/navbar.component';
import {TranslateModule} from '@ngx-translate/core';

/**
 * The shell component for the user account section.
 *
 * This component provides the main layout for all account-related pages, including
 * the main navigation bar, a side menu for account navigation, and a router outlet
 * for displaying child components like `ProfileComponent` and `SettingsComponent`.
 */
@Component({
  selector: 'app-account',
  standalone: true,
  imports: [RouterModule, NavbarComponent, TranslateModule],
  templateUrl: './account.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountComponent {
  // This component is purely presentational and holds no business logic itself.
}
