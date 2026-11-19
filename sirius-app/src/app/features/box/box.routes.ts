import {Routes} from '@angular/router';
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
  // Records board of the box (lot C, D56)
  {path: 'leaderboards', loadComponent: () => import('../records/box-records.component').then(c => c.BoxRecordsComponent)},
  // Balance of the programming, staff of any role (lot D', D167)
  {
    path: 'programming', canActivate: [gymPermissionGuard('STAFF')],
    loadComponent: () => import('../programming/programming-balance.component').then(c => c.ProgrammingBalanceComponent)
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
