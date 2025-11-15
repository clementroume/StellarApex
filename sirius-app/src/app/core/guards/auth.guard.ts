import { inject } from '@angular/core';
import { CanActivateFn, Router, RouterStateSnapshot } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { map, tap } from 'rxjs';

/**
 * A functional route guard that protects routes from unauthenticated access.
 *
 * @param _ The route that is being activated (unused).
 * @param state The current router state, used for redirection.
 * @returns A boolean or Observable<boolean> indicating if activation is allowed.
 */
export const authGuard: CanActivateFn = (_, state: RouterStateSnapshot) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  // Case 1: The initial authentication check has already been performed.
  if (authService.currentUser() !== undefined) {
    const isAuthenticated = authService.isAuthenticated();
    if (!isAuthenticated) {
      // If not authenticated, redirect to the login page.
      void router.navigate(['/auth/login'], { queryParams: { returnUrl: state.url } });
    }
    return isAuthenticated;
  }

  // Case 2: Initial application load. Trigger the user fetch.
  return authService.initCurrentUser().pipe(
    map(() => authService.isAuthenticated()), // Check the status after the API call completes.
    tap(isAuthenticated => {
      if (!isAuthenticated) {
        // If the check reveals no user, redirect to login.
        void router.navigate(['/auth/login'], { queryParams: { returnUrl: state.url } });
      }
    })
  );
};
