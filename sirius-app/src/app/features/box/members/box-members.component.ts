import {ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked} from '@angular/core';
import {RouterLink} from '@angular/router';
import {NgTemplateOutlet} from '@angular/common';
import {Observable} from 'rxjs';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {NgIcon} from '@ng-icons/core';
import {HlmBadge} from '@spartan-ng/helm/badge';
import {HlmButton} from '@spartan-ng/helm/button';
import {HlmSpinner} from '@spartan-ng/helm/spinner';
import {MembershipService} from '../../../api/antares/services/membership.service';
import {
  GymRole,
  MembershipResponse,
  MembershipStatus,
  Permission
} from '../../../api/antares/models/membership.model';
import {AccessService} from '../../../core/services/access.service';
import {NotificationService} from '../../../core/services/notification.service';
import {problemDetailOf} from '../../../core/models/problem-detail.model';
import {PageComponent} from '../../../shared/component/page/page.component';
import {ConfirmDialogComponent} from '../../../shared/component/confirm-dialog/confirm-dialog.component';
import {NativeSelectDirective} from '../../../shared/component/native-select/native-select.directive';
import {StaffGymPickerComponent, staffGymSelection} from '../staff-gym';

export const MEMBER_STATUSES: MembershipStatus[] = ['PENDING', 'ACTIVE', 'INACTIVE', 'BANNED'];
export const ALL_PERMISSIONS: Permission[] = ['WOD_WRITE', 'SCORE_VERIFY', 'MANAGE_MEMBERSHIPS', 'MANAGE_SETTINGS'];

/** Confirmed action on a member. */
type MemberAction = {kind: 'refuse' | 'remove' | 'ban'; member: MembershipResponse};

/**
 * My box › Members (MANAGE_MEMBERSHIPS): pending requests first, status and role filters, and the
 * actions Antares allows on each member (`capabilities`): validate, refuse, remove, ban, role and
 * permissions. Nothing is decided here: buttons only follow the capabilities.
 */
@Component({
  selector: 'app-box-members',
  standalone: true,
  imports: [RouterLink, NgTemplateOutlet, TranslatePipe, NgIcon, HlmBadge, HlmButton, HlmSpinner, PageComponent, ConfirmDialogComponent,
    NativeSelectDirective, StaffGymPickerComponent],
  templateUrl: './box-members.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BoxMembersComponent {
  // --- INJECTIONS ---
  private readonly membershipService = inject(MembershipService);
  private readonly accessService = inject(AccessService);
  private readonly notificationService = inject(NotificationService);
  private readonly translate = inject(TranslateService);

  // --- STATE (SIGNALS & VARIABLES) ---
  readonly staffGym = staffGymSelection('MANAGE_MEMBERSHIPS');
  readonly members = signal<MembershipResponse[]>([]);
  readonly isLoading = signal<boolean>(false);
  readonly busyId = signal<number | null>(null);
  readonly statusFilter = signal<MembershipStatus | null>(null);
  readonly roleFilter = signal<GymRole | null>(null);
  readonly editingId = signal<number | null>(null);
  readonly editRole = signal<GymRole>('ATHLETE');
  readonly editPermissions = signal<Permission[]>([]);
  readonly pendingAction = signal<MemberAction | null>(null);
  readonly statuses = MEMBER_STATUSES;
  readonly permissions = ALL_PERMISSIONS;

  // --- COMPUTED STATE ---
  readonly roles = computed<GymRole[]>(() =>
    this.staffGym.membership()?.gymProgramming ? ['PROGRAMMER', 'ATHLETE'] : ['OWNER', 'COACH', 'ATHLETE']
  );
  private readonly filtered = computed(() => this.members().filter(m =>
    (this.statusFilter() === null || m.status === this.statusFilter())
    && (this.roleFilter() === null || m.gymRole === this.roleFilter())
  ));
  readonly pending = computed(() => this.filtered().filter(m => m.status === 'PENDING'));
  readonly others = computed(() => this.filtered().filter(m => m.status !== 'PENDING'));
  readonly canInvite = computed(() => this.accessService.hasGymPermission(this.staffGym.gymId(), 'MANAGE_SETTINGS'));
  readonly userId = this.accessService.userId;

  // --- LIFECYCLE ---
  constructor() {
    effect(() => {
      const gymId = this.staffGym.gymId();
      untracked(() => this.load(gymId));
    });
  }

  // --- DATA FETCHING ---
  load(gymId: number | null = this.staffGym.gymId()): void {
    this.editingId.set(null);
    if (gymId === null) {
      this.members.set([]);
      return;
    }
    this.isLoading.set(true);
    this.membershipService.getMemberships(gymId).subscribe({
      next: members => {
        this.members.set(members);
        this.isLoading.set(false);
      },
      error: err => {
        this.members.set([]);
        this.isLoading.set(false);
        this.showError(err);
      }
    });
  }

  // --- ACTIONS & EVENTS ---
  onStatusFilter(status: MembershipStatus | null): void {
    this.statusFilter.set(status);
  }

  onRoleFilter(value: string): void {
    this.roleFilter.set((value || null) as GymRole | null);
  }

  /** Validate a request, reactivate a former member, or lift a ban (back to INACTIVE). */
  setStatus(member: MembershipResponse, status: MembershipStatus): void {
    this.update(member, {status, gymRole: member.gymRole, permissions: member.permissions});
  }

  ask(kind: MemberAction['kind'], member: MembershipResponse): void {
    this.pendingAction.set({kind, member});
  }

  cancelAction(): void {
    this.pendingAction.set(null);
  }

  confirmAction(): void {
    const action = this.pendingAction();
    if (!action) return;
    this.pendingAction.set(null);
    if (action.kind === 'ban') {
      this.setStatus(action.member, 'BANNED');
      return;
    }
    const name = this.fullName(action.member);
    this.run(action.member, this.membershipService.deleteMembership(action.member.id), () => {
      this.notificationService.showSuccess(
        this.translate.instant(action.kind === 'refuse' ? 'BOX.MEMBERS.REFUSED' : 'BOX.MEMBERS.REMOVED', {name}));
      this.load();
    });
  }

  startEdit(member: MembershipResponse): void {
    this.editingId.set(member.id);
    this.editRole.set(member.gymRole);
    this.editPermissions.set([...member.permissions]);
  }

  cancelEdit(): void {
    this.editingId.set(null);
  }

  onEditRole(value: string): void {
    this.editRole.set(value as GymRole);
  }

  togglePermission(permission: Permission, checked: boolean): void {
    this.editPermissions.update(current =>
      checked ? [...current.filter(p => p !== permission), permission] : current.filter(p => p !== permission));
  }

  saveEdit(member: MembershipResponse): void {
    const role = this.editRole();
    // Permissions only apply to coaches: an athlete keeps none
    const permissions = role === 'COACH' ? this.permissions.filter(p => this.editPermissions().includes(p)) : [];
    this.update(member, {status: member.status, gymRole: role, permissions});
  }

  // --- HELPERS ---
  fullName(member: MembershipResponse): string {
    return `${member.user.firstName} ${member.user.lastName}`;
  }

  formatDate(value: string): string {
    return new Date(value).toLocaleDateString(this.translate.currentLang() ?? undefined);
  }

  /** Roles a member may receive here: the holder role only changes through a transfer. */
  editableRoles(): GymRole[] {
    return this.roles().filter(r => r !== 'OWNER' && r !== 'PROGRAMMER');
  }

  /** Translation keys of the confirmation dialog for the pending action. */
  dialogKeys(kind: MemberAction['kind']): {title: string; description: string; confirm: string} {
    const prefix = {refuse: 'REFUSE', remove: 'REMOVE', ban: 'BAN'}[kind];
    return {title: `BOX.MEMBERS.${prefix}_TITLE`, description: `BOX.MEMBERS.${prefix}_DESC`, confirm: `BOX.MEMBERS.${prefix}`};
  }

  private update(member: MembershipResponse, payload: {status: MembershipStatus; gymRole: GymRole; permissions: Permission[]}): void {
    this.run(member, this.membershipService.updateMembership(member.id, payload), updated => {
      this.members.update(list => list.map(m => (m.id === updated.id ? updated : m)));
      this.editingId.set(null);
      this.notificationService.showSuccess(this.translate.instant('BOX.MEMBERS.UPDATED', {name: this.fullName(member)}));
    });
  }

  private run<T>(member: MembershipResponse, request$: Observable<T>, onSuccess: (value: T) => void): void {
    this.busyId.set(member.id);
    request$.subscribe({
      next: value => {
        this.busyId.set(null);
        onSuccess(value);
      },
      error: err => {
        this.busyId.set(null);
        this.showError(err);
      }
    });
  }

  private showError(err: unknown): void {
    this.notificationService.showError(problemDetailOf(err) ?? this.translate.instant('GLOBAL.ERROR_UNEXPECTED'));
  }
}
