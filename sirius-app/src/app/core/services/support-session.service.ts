import {computed, inject, Injectable} from '@angular/core';
import {Router} from '@angular/router';
import {TranslateService} from '@ngx-translate/core';
import {catchError, map, Observable, of, switchMap, tap} from 'rxjs';
import {AuthService} from '../../api/antares/services/auth.service';
import {AdminService} from '../../api/antares/services/admin.service';
import {NotificationService} from './notification.service';

/**
 * Admin support session: a platform admin acts as a real user (audited, short-lived, writable).
 *
 * <p>Antares keeps the admin's own session and switches the request identity through a dedicated
 * cookie; this service only starts/ends it and reloads the current user so the whole UI follows.
 */
@Injectable({providedIn: 'root'})
export class SupportSessionService {
  // --- INJECTIONS ---
  private readonly authService = inject(AuthService);
  private readonly adminService = inject(AdminService);
  private readonly notificationService = inject(NotificationService);
  private readonly translate = inject(TranslateService);
  private readonly router = inject(Router);

  // --- STATE ---
  private handlingExpiry = false;

  // --- COMPUTED STATE ---
  readonly session = computed(() => this.authService.currentUser()?.supportSession ?? null);
  readonly isActive = computed(() => this.session() !== null);

  // --- ACTIONS ---
  start(userId: number, reason: string): Observable<void> {
    return this.adminService.startSupportSession({userId, reason}).pipe(
      switchMap(() => this.authService.initCurrentUser()),
      tap(() => void this.router.navigate(['/dashboard'])),
      map(() => undefined)
    );
  }

  end(): Observable<void> {
    return this.adminService.endSupportSession().pipe(
      // Ending is idempotent on the server: reload the admin identity whatever happens
      catchError(() => of(undefined)),
      switchMap(() => this.authService.initCurrentUser()),
      tap(() => void this.router.navigate(['/admin/support'])),
      map(() => undefined)
    );
  }

  /** Called when Antares reports an expired session: back to the admin identity, never silently. */
  handleExpired(): void {
    if (this.handlingExpiry) return;
    this.handlingExpiry = true;

    this.authService.initCurrentUser().subscribe(() => {
      this.handlingExpiry = false;
      this.notificationService.showError(this.translate.instant('ADMIN.SUPPORT.EXPIRED'));
      void this.router.navigate(['/admin/support']);
    });
  }
}
