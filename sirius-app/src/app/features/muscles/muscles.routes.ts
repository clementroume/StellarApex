import {Routes} from '@angular/router';
import {MuscleListComponent} from './muscle-list/muscle-list.component';
import {MuscleFormComponent} from './muscle-form/muscle-form.component';
import {adminGuard} from '../../core/guards/admin.guard';

export const MUSCLE_ROUTES: Routes = [
  {
    path: '',
    component: MuscleListComponent
  },
  {
    path: 'new',
    component: MuscleFormComponent,
    canActivate: [adminGuard]
  },
  {
    path: 'edit/:id',
    component: MuscleFormComponent,
    canActivate: [adminGuard]
  }
];
