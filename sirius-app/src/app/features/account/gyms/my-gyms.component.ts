import {ChangeDetectionStrategy, Component, computed, inject, signal} from '@angular/core';
import {RouterLink} from '@angular/router';
import {Observable} from 'rxjs';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {NgIcon} from '@ng-icons/core';
import {HlmBadge} from '@spartan-ng/helm/badge';
import {HlmButton} from '@spartan-ng/helm/button';
import {HlmCardImports} from '@spartan-ng/helm/card';
import {AuthService} from '../../../api/antares/services/auth.service';
import {UserService} from '../../../api/antares/services/user.service';
import {GymService} from '../../../api/antares/services/gym.service';
import {GymRole, MembershipSummary} from '../../../api/antares/models/membership.model';
import {NotificationService} from '../../../core/services/notification.service';
import {problemDetailOf} from '../../../core/models/problem-detail.model';
import {ConfirmDialogComponent} from '../../../shared/component/confirm-dialog/confirm-dialog.component';

/** Confirmed action on one of the user's memberships. */
type PendingAction = {kind: 'leave' | 'withdraw' | 'deleteRequest'; membership: MembershipSummary};

const HOLDER_ROLES: GymRole[] = ['OWNER', 'PROGRAMMER'];

/**
 * My boxes & programs: current memberships (role, status, permissions), pending requests, former
 * memberships, and the leave / withdraw actions. Antares decides: leaving as Owner/Programmer or
 * banned is refused there (409), the UI only avoids offering it.
 */
@Component({
  selector: 'app-my-gyms',
  standalone: true,
  imports: [RouterLink, TranslatePipe, NgIcon, HlmBadge, HlmButton, HlmCardImports, ConfirmDialogComponent],
  templateUrl: './my-gyms.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MyGymsComponent {
  // --- INJECTIONS ---
  private readonly authService = inject(AuthService);
  private readonly userService = inject(UserService);
  private readonly gymService = inject(GymService);
  private readonly notificationService = inject(NotificationService);
  private readonly translate = inject(TranslateService);

  // --- STATE (SIGNALS & VARIABLES) ---
  readonly pendingAction = signal<PendingAction | null>(null);
  readonly isWorking = signal<boolean>(false);

  // --- COMPUTED STATE ---
  private readonly all = computed(() => this.authService.currentUser()?.memberships ?? []);
  readonly current = computed(() => this.all().filter(m => m.status !== 'INACTIVE'));
  readonly former = computed(() => this.all().filter(m => m.status === 'INACTIVE'));

  // --- ACTIONS & EVENTS ---
  ask(kind: PendingAction['kind'], membership: MembershipSummary): void {
    this.pendingAction.set({kind, membership});
  }

  cancel(): void {
    if (!this.isWorking()) this.pendingAction.set(null);
  }

  confirm(): void {
    const action = this.pendingAction();
    if (!action || this.isWorking()) return;
    const {kind, membership} = action;
    const request$: Observable<void> = kind === 'deleteRequest'
      ? this.gymService.deleteGym(membership.gymId)
      : this.gymService.leaveGym(membership.gymId);
    const successKey = {leave: 'MY_GYMS.LEFT', withdraw: 'MY_GYMS.WITHDRAWN', deleteRequest: 'MY_GYMS.REQUEST_DELETED'}[kind];

    this.isWorking.set(true);
    request$.subscribe({
      next: () => {
        this.isWorking.set(false);
        this.pendingAction.set(null);
        this.notificationService.showSuccess(this.translate.instant(successKey, {name: membership.gymName}));
        this.userService.refreshCurrentUser().subscribe();
      },
      error: err => {
        this.isWorking.set(false);
        this.pendingAction.set(null);
        this.notificationService.showError(problemDetailOf(err) ?? this.translate.instant('GLOBAL.ERROR_UNEXPECTED'));
      }
    });
  }

  // --- HELPERS ---
  isHolder(m: MembershipSummary): boolean {
    return HOLDER_ROLES.includes(m.gymRole);
  }

  /** Translation keys of the confirmation dialog for the pending action. */
  dialogKeys(kind: PendingAction['kind']): {title: string; description: string; confirm: string} {
    switch (kind) {
      case 'leave':
        return {title: 'MY_GYMS.LEAVE_TITLE', description: 'MY_GYMS.LEAVE_DESC', confirm: 'MY_GYMS.LEAVE'};
      case 'withdraw':
        return {title: 'MY_GYMS.WITHDRAW_TITLE', description: 'MY_GYMS.WITHDRAW_DESC', confirm: 'MY_GYMS.WITHDRAW'};
      case 'deleteRequest':
        return {title: 'MY_GYMS.DELETE_REQUEST_TITLE', description: 'MY_GYMS.DELETE_REQUEST_DESC', confirm: 'MY_GYMS.DELETE_REQUEST'};
    }
  }
}
