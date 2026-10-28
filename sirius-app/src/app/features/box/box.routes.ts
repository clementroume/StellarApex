import {Routes} from '@angular/router';
import {SpacePlaceholderComponent} from '../../shared/component/coming-soon/space-placeholder.component';
import {gymPermissionGuard} from '../../core/guards/gym-permission.guard';

/** My box: athlete pages, then staff pages opened by permission. */
export const BOX_ROUTES: Routes = [
  {path: '', redirectTo: 'sessions', pathMatch: 'full'},
  {
    path: 'sessions', component: SpacePlaceholderComponent,
    data: {titleKey: 'BOX.SESSIONS.TITLE', descriptionKey: 'BOX.SESSIONS.COMING', icon: 'heroCalendarDays'}
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
