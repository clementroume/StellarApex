import { Routes } from '@angular/router';
import { DashboardComponent } from './features/dashboard/dashboard.component';
import { authGuard } from './core/guards/auth.guard';
import { AccountComponent } from './features/account/account.component';

/**
 * Defines the main application routes.
 *
 * This configuration is provided to the Angular Router to set up the application's navigation.
 * It leverages lazy loading for feature modules to optimize initial load times and
 * uses route guards to protect authenticated sections.
 */
export const routes: Routes = [
  {
    // The '/auth' path handles user authentication (login, registration).
    // It is lazy-loaded, meaning its code is only fetched when a user navigates to it.
    path: 'auth',
    loadChildren: () => import('./features/auth/auth.routes').then(m => m.AUTH_ROUTES)
  },
  {
    // The main dashboard route, which is the default view for authenticated users.
    // It is protected by the `authGuard` to prevent access by unauthenticated users.
    path: 'dashboard',
    component: DashboardComponent,
    canActivate: [authGuard]
  },
  {
    // The account management section, also protected and lazy-loaded.
    path: 'my-account',
    component: AccountComponent,
    loadChildren: () => import('./features/account/account.routes').then(r => r.ACCOUNT_ROUTES),
    canActivate: [authGuard]
  },
  {
    // Redirects the root path ('/') to the dashboard, which will then be
    // handled by the authGuard (redirecting to login if not authenticated).
    path: '',
    redirectTo: '/dashboard',
    pathMatch: 'full'
  },
  {
    // A wildcard route that catches any URL that doesn't match the routes above
    // and redirects the user to the dashboard.
    path: '**',
    redirectTo: '/dashboard'
  }
];
