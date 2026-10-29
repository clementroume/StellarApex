import {Routes} from '@angular/router';
import {SpacePlaceholderComponent} from '../../shared/component/coming-soon/space-placeholder.component';

export const TRAINING_ROUTES: Routes = [
  {path: '', redirectTo: 'journal', pathMatch: 'full'},
  {path: 'journal', loadComponent: () => import('./training-journal.component').then(c => c.TrainingJournalComponent)},
  // Personal WODs: quick editor (the WOD catalogue lists public WODs only)
  {path: 'wods', loadComponent: () => import('./my-wods/my-wods.component').then(c => c.MyWodsComponent)},
  {path: 'wods/new', loadComponent: () => import('./my-wods/my-wod-form.component').then(c => c.MyWodFormComponent)},
  {path: 'wods/:id/edit', loadComponent: () => import('./my-wods/my-wod-form.component').then(c => c.MyWodFormComponent)},
  // Personal sessions (D15): author only, no gym nor visibility policy
  {
    path: 'sessions/new', data: {personal: true},
    loadComponent: () => import('../sessions/session-form/session-form.component').then(c => c.SessionFormComponent)
  },
  {
    path: 'sessions/:id',
    loadComponent: () => import('../sessions/session-detail/session-detail.component').then(c => c.SessionDetailComponent)
  },
  {
    path: 'sessions/:id/edit', data: {personal: true},
    loadComponent: () => import('../sessions/session-form/session-form.component').then(c => c.SessionFormComponent)
  },
  {
    path: 'records', component: SpacePlaceholderComponent,
    data: {titleKey: 'TRAINING.RECORDS.TITLE', descriptionKey: 'TRAINING.RECORDS.COMING', icon: 'heroTrophy'}
  },
  {
    path: 'review', component: SpacePlaceholderComponent,
    data: {titleKey: 'TRAINING.REVIEW.TITLE', descriptionKey: 'TRAINING.REVIEW.COMING', icon: 'heroArrowTrendingUp'}
  },
];
