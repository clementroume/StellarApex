import {Routes} from '@angular/router';
import {SpacePlaceholderComponent} from '../../shared/component/coming-soon/space-placeholder.component';
import {gymPermissionGuard} from '../../core/guards/gym-permission.guard';

/** My box: athlete pages, then staff pages opened by permission (lot A fills them). */
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
    path: 'members', component: SpacePlaceholderComponent, canActivate: [gymPermissionGuard('MANAGE_MEMBERSHIPS')],
    data: {titleKey: 'BOX.MEMBERS.TITLE', descriptionKey: 'BOX.MEMBERS.COMING', icon: 'heroUsers'}
  },
  {
    path: 'settings', component: SpacePlaceholderComponent, canActivate: [gymPermissionGuard('MANAGE_SETTINGS')],
    data: {titleKey: 'BOX.SETTINGS.TITLE', descriptionKey: 'BOX.SETTINGS.COMING', icon: 'heroAdjustmentsHorizontal'}
  },
];
