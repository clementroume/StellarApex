import {inject} from '@angular/core';
import {CanActivateFn, Router} from '@angular/router';
import {AccessService} from '../services/access.service';

export const adminGuard: CanActivateFn = () => {
  if (inject(AccessService).isAdmin()) {
    return true;
  }

  // Redirection silencieuse si un utilisateur malin tape l'URL à la main
  void inject(Router).navigate(['/today']);
  return false;
};
