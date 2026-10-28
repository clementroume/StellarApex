import {computed, inject, Injectable} from '@angular/core';
import {AuthService} from '../../api/antares/services/auth.service';
import {MembershipSummary, Permission} from '../../api/antares/models/membership.model';

/**
 * Single source of truth for UI rights. Mirrors what Antares sends to Aldebaran (X-Auth-Memberships)
 * so that buttons only show for profiles the backend will accept. During a support session the
 * current user is the target user, so these rights are the target's.
 */
@Injectable({providedIn: 'root'})
export class AccessService {
  // --- INJECTIONS ---
  private readonly authService = inject(AuthService);

  // --- COMPUTED STATE ---
  readonly userId = computed(() => this.authService.currentUser()?.id ?? null);
  readonly isAdmin = computed(() => this.authService.currentUser()?.platformRole === 'ADMIN');

  /** Memberships Antares accepts (`usable`, computed by Antares from Membership.isUsable). */
  readonly memberships = computed<MembershipSummary[]>(() =>
    (this.authService.currentUser()?.memberships ?? []).filter(m => m.usable)
  );

  // --- HELPERS ---
  hasGymPermission(gymId: number | null | undefined, permission: Permission): boolean {
    if (this.isAdmin()) return true;
    if (gymId === null || gymId === undefined) return false;
    return this.memberships().some(m => m.gymId === gymId && (m.permissions ?? []).includes(permission));
  }

  gymsWithPermission(permission: Permission): MembershipSummary[] {
    return this.memberships().filter(m => this.isAdmin() || (m.permissions ?? []).includes(permission));
  }
}
