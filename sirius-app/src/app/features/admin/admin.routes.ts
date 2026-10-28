import {Routes} from '@angular/router';
import {adminGuard} from '../../core/guards/admin.guard';
import {SupportComponent} from './support/support.component';

export const ADMIN_ROUTES: Routes = [
  {path: '', redirectTo: 'gyms', pathMatch: 'full'},
  {
    path: 'gyms',
    canActivate: [adminGuard],
    loadComponent: () => import('./gyms/admin-gyms.component').then(c => c.AdminGymsComponent)
  },
  {
    path: 'support',
    component: SupportComponent,
    canActivate: [adminGuard]
  }
];
