import {Routes} from '@angular/router';
import {ProfileComponent} from './profile/profile.component';
import {SettingsComponent} from './settings/settings.component';
import {UnitsComponent} from './units/units.component';
import {AccountComponent} from './account.component';

export const ACCOUNT_ROUTES: Routes = [
  {
    path: '',
    component: AccountComponent,
    children: [
      {
        path: 'profile',
        component: ProfileComponent
      },
      {
        path: 'units',
        component: UnitsComponent
      },
      {
        path: 'settings',
        component: SettingsComponent
      },
      {
        path: '',
        redirectTo: 'profile',
        pathMatch: 'full'
      }
    ]
  }
];
