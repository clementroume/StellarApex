import {Routes} from '@angular/router';
import {MovementListComponent} from './movement-list/movement-list.component';
import {adminGuard} from '../../core/guards/admin.guard';
import {MovementFormComponent} from './movement-form/movement-form.component';

export const MOVEMENT_ROUTES: Routes = [
  {
    path: '',
    component: MovementListComponent
  },
  {
    path: 'new',
    component: MovementFormComponent,
    canActivate: [adminGuard]
  },
  {
    path: 'edit/:id',
    component: MovementFormComponent,
    canActivate: [adminGuard]
  }
];
