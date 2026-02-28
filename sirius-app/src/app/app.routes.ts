import {Routes} from '@angular/router';
import {DashboardComponent} from './features/dashboard/dashboard.component';
import {AccountComponent} from './features/account/account.component';
import {MainLayoutComponent} from './core/layout/main-layout/main-layout.component';
import {authGuard} from './core/guards/auth.guard';

export const routes: Routes = [
  // 1. PUBLIC ROUTES
  {
    path: 'auth',
    loadChildren: () => import('./features/auth/auth.routes').then(m => m.AUTH_ROUTES)
  },

  // 2. PRIVATE ROUTES
  {
    path: '',
    component: MainLayoutComponent,
    canActivate: [authGuard],
    children: [
      {
        path: '',
        redirectTo: 'dashboard',
        pathMatch: 'full'
      },
      {
        path: 'dashboard',
        component: DashboardComponent
      },
      {
        path: 'my-account',
        component: AccountComponent,
        loadChildren: () => import('./features/account/account.routes').then(r => r.ACCOUNT_ROUTES)
      },
      {
        path: 'muscles',
        loadChildren: () => import('./features/muscles/muscles.routes').then(m => m.MUSCLE_ROUTES)
      }
    ]
  },

  // 3. CATCH-ALL FALLBACK
  {
    path: '**',
    redirectTo: '/dashboard'
  }
];
