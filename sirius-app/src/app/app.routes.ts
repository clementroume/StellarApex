import {Routes} from '@angular/router';
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
        redirectTo: 'today',
        pathMatch: 'full'
      },
      // Spaces (D39): Today · My training · My box · Catalogue (wods, movements, muscles) · Admin
      {
        path: 'today',
        loadComponent: () => import('./features/today/today.component').then(c => c.TodayComponent)
      },
      {
        path: 'training',
        loadChildren: () => import('./features/training/training.routes').then(r => r.TRAINING_ROUTES)
      },
      {
        path: 'box',
        loadChildren: () => import('./features/box/box.routes').then(r => r.BOX_ROUTES)
      },
      // Former home, kept for bookmarks
      {path: 'dashboard', redirectTo: 'today'},
      {
        path: 'my-account',
        loadChildren: () => import('./features/account/account.routes').then(r => r.ACCOUNT_ROUTES)
      },
      {
        path: 'muscles',
        loadChildren: () => import('./features/muscles/muscles.routes').then(m => m.MUSCLE_ROUTES)
      },
      {
        path: 'movements',
        loadChildren: () => import('./features/movements/movements.routes').then(m => m.MOVEMENT_ROUTES)
      },
      {
        path: 'wods',
        loadChildren: () => import('./features/wods/wods.routes').then(m => m.WOD_ROUTES)
      },
      {
        path: 'admin',
        loadChildren: () => import('./features/admin/admin.routes').then(m => m.ADMIN_ROUTES)
      },
    ]
  },

  // 3. CATCH-ALL FALLBACK
  {
    path: '**',
    redirectTo: '/today'
  }
];
