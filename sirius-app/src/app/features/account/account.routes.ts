import { Routes } from '@angular/router';
import { ProfileComponent } from './profile/profile.component';
import { SettingsComponent } from './settings/settings.component';

/**
 * Defines the child routes for the 'Account' feature area.
 * <p>
 * These routes are lazy-loaded within the main application router and are
 * rendered inside the {@link AccountComponent}'s <router-outlet>.
 */
export const ACCOUNT_ROUTES: Routes = [
  {
    path: 'profile',
    component: ProfileComponent
  },
  {
    path: 'settings',
    component: SettingsComponent
  },
  {
    // This is the default route for the '/my-account' path.
    // It redirects an empty '/my-account' URL to '/my-account/profile'.
    path: '',
    redirectTo: 'profile',
    pathMatch: 'full'
  }
];
