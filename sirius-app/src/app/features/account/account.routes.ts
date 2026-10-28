import {Routes} from '@angular/router';
import {ProfileComponent} from './profile/profile.component';
import {SettingsComponent} from './settings/settings.component';
import {UnitsComponent} from './units/units.component';
import {AccountComponent} from './account.component';
import {AthleteProfileComponent} from './athlete/athlete-profile.component';
import {MyGymsComponent} from './gyms/my-gyms.component';
import {JoinGymComponent} from './gyms/join-gym.component';
import {GymRequestComponent} from './gyms/gym-request.component';

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
        path: 'athlete',
        component: AthleteProfileComponent
      },
      {
        path: 'gyms',
        component: MyGymsComponent
      },
      {
        path: 'gyms/join',
        component: JoinGymComponent
      },
      {
        path: 'gyms/new',
        component: GymRequestComponent
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
