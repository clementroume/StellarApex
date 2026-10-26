import {Routes} from '@angular/router';
import {adminGuard} from '../../core/guards/admin.guard';
import {SupportComponent} from './support/support.component';

export const ADMIN_ROUTES: Routes = [
  {
    path: 'support',
    component: SupportComponent,
    canActivate: [adminGuard]
  }
];
