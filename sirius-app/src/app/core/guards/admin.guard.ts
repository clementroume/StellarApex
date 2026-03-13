import {inject} from '@angular/core';
import {CanActivateFn, Router} from '@angular/router';
import {AuthService} from '../../api/antares/services/auth.service';

export const adminGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const user = authService.currentUser();

  if (user?.platformRole === 'ADMIN') {
    return true;
  }

  // Redirection silencieuse si un utilisateur malin tape l'URL à la main
  void router.navigate(['/dashboard']);
  return false;
};
