import {Routes} from '@angular/router';
import {WodListComponent} from './wod-list/wod-list.component';
import {WodFormComponent} from './wod-form/wod-form.component';
import {WodDetailComponent} from './wod-detail/wod-detail.component';

// No guard on creation/edition: any user may author a personal WOD, Aldebaran enforces the rights
export const WOD_ROUTES: Routes = [
  {
    path: '',
    component: WodListComponent
  },
  {
    path: 'new',
    component: WodFormComponent
  },
  {
    path: 'edit/:id',
    component: WodFormComponent
  },
  {
    path: ':id',
    component: WodDetailComponent
  }
];
