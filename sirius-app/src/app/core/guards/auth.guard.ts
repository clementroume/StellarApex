import {inject} from '@angular/core';
import {CanActivateFn, Router, RouterStateSnapshot, UrlTree} from '@angular/router';
import {AuthService} from '../../api/antares/services/auth.service';
import {map} from 'rxjs';

/**
 * Opens the app to signed-in users only. Anonymous users are redirected (as a returned UrlTree, so
 * the router replaces the current navigation instead of racing it) to the login page with the
 * requested URL as `returnUrl`, e.g. an invitation link `/join?gym=12&code=ABC`.
 */
export const authGuard: CanActivateFn = (_, state: RouterStateSnapshot) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const loginTree = (): UrlTree => router.createUrlTree(['/auth/login'], {queryParams: {returnUrl: state.url}});

  // Case 1: The initial authentication check has already been performed.
  if (authService.currentUser() !== undefined) {
    return authService.isAuthenticated() || loginTree();
  }

  // Case 2: Initial application load. Trigger the user fetch.
  return authService.initCurrentUser().pipe(
    map(() => authService.isAuthenticated() || loginTree())
  );
};
