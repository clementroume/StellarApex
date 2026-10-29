import {Routes} from '@angular/router';
import {SpacePlaceholderComponent} from '../../shared/component/coming-soon/space-placeholder.component';
import {gymPermissionGuard} from '../../core/guards/gym-permission.guard';

/** My box: athlete pages, then staff pages opened by permission. */
export const BOX_ROUTES: Routes = [
  {path: '', redirectTo: 'sessions', pathMatch: 'full'},
  {
    path: 'sessions',
    loadComponent: () => import('../sessions/session-week/session-week.component').then(c => c.SessionWeekComponent)
  },
  {
    path: 'sessions/new', canActivate: [gymPermissionGuard('WOD_WRITE')],
    loadComponent: () => import('../sessions/session-form/session-form.component').then(c => c.SessionFormComponent)
  },
  {
    path: 'sessions/:id',
    loadComponent: () => import('../sessions/session-detail/session-detail.component').then(c => c.SessionDetailComponent)
  },
  {
    path: 'sessions/:id/edit', canActivate: [gymPermissionGuard('WOD_WRITE')],
    loadComponent: () => import('../sessions/session-form/session-form.component').then(c => c.SessionFormComponent)
  },
  {
    path: 'leaderboards', component: SpacePlaceholderComponent,
    data: {titleKey: 'BOX.LEADERBOARDS.TITLE', descriptionKey: 'BOX.LEADERBOARDS.COMING', icon: 'heroChartBar'}
  },
  {
    path: 'members', canActivate: [gymPermissionGuard('MANAGE_MEMBERSHIPS')],
    loadComponent: () => import('./members/box-members.component').then(c => c.BoxMembersComponent)
  },
  {
    path: 'settings', canActivate: [gymPermissionGuard('MANAGE_SETTINGS')],
    loadComponent: () => import('./settings/box-settings.component').then(c => c.BoxSettingsComponent)
  },
];
