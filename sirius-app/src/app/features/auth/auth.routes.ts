import {Routes} from '@angular/router';
import {LoginComponent} from './login/login.component';
import {RegisterComponent} from './register/register.component';
import {AuthComponent} from './auth.component';

/**
 * Defines the child routes for the authentication feature area.
 * These routes are lazy-loaded when the user navigates to the '/auth' path.
 */
export const AUTH_ROUTES: Routes = [
  {
    // Le composant parent qui sert de layout pour l'authentification
    path: '',
    component: AuthComponent,
    children: [
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
        path: '',
        redirectTo: 'login',
        pathMatch: 'full'
      }
    ]
  }
];
