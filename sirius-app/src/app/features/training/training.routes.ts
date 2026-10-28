import {Routes} from '@angular/router';
import {SpacePlaceholderComponent} from '../../shared/component/coming-soon/space-placeholder.component';

export const TRAINING_ROUTES: Routes = [
  {path: '', redirectTo: 'journal', pathMatch: 'full'},
  {path: 'journal', loadComponent: () => import('./training-journal.component').then(c => c.TrainingJournalComponent)},
  {
    path: 'records', component: SpacePlaceholderComponent,
    data: {titleKey: 'TRAINING.RECORDS.TITLE', descriptionKey: 'TRAINING.RECORDS.COMING', icon: 'heroTrophy'}
  },
  {
    path: 'review', component: SpacePlaceholderComponent,
    data: {titleKey: 'TRAINING.REVIEW.TITLE', descriptionKey: 'TRAINING.REVIEW.COMING', icon: 'heroArrowTrendingUp'}
  },
];
