import { Routes } from '@angular/router';
import { LoginComponent } from './login/login.component';
import { RegisterComponent } from './register/register.component';

/**
 * Defines the child routes for the authentication feature area.
 * These routes are lazy-loaded when the user navigates to the '/auth' path.
 */
export const AUTH_ROUTES: Routes = [
  {
    // The route for the user login page.
    path: 'login',
    component: LoginComponent
  },
  {
    // The route for the new user registration page.
    path: 'register',
    component: RegisterComponent
  },
  {
    // Default route for the '/auth' path.
    // Redirects any navigation to '/auth' directly to '/auth/login'.
    path: '',
    redirectTo: 'login',
    pathMatch: 'full'
  }
];
