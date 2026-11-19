import {inject} from '@angular/core';
import {CanActivateFn, Router} from '@angular/router';
import {AccessService} from '../services/access.service';
import {Permission} from '../../api/antares/models/membership.model';

/**
 * Opens a staff screen only to users holding the permission on at least one gym. UI comfort only:
 * the backend checks the permission on the gym of every request.
 */
export const gymPermissionGuard = (permission: Permission | 'STAFF'): CanActivateFn => () => {
  const access = inject(AccessService);
  if ((permission === 'STAFF' ? access.staffGyms() : access.gymsWithPermission(permission)).length > 0) {
    return true;
  }
  return inject(Router).createUrlTree(['/box']);
};
