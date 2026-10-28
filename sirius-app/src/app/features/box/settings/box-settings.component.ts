import {ChangeDetectionStrategy, Component, computed, DOCUMENT, effect, inject, signal, untracked} from '@angular/core';
import {Router} from '@angular/router';
import {Observable} from 'rxjs';
import {FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {NgIcon} from '@ng-icons/core';
import {HlmAlertImports} from '@spartan-ng/helm/alert';
import {HlmButton} from '@spartan-ng/helm/button';
import {HlmCardImports} from '@spartan-ng/helm/card';
import {HlmInputGroupImports} from '@spartan-ng/helm/input-group';
import {HlmInput} from '@spartan-ng/helm/input';
import {HlmLabel} from '@spartan-ng/helm/label';
import {HlmSpinner} from '@spartan-ng/helm/spinner';
import {HlmSwitchImports} from '@spartan-ng/helm/switch';
import {HlmTextarea} from '@spartan-ng/helm/textarea';
import {GymService} from '../../../api/antares/services/gym.service';
import {MembershipService} from '../../../api/antares/services/membership.service';
import {UserService} from '../../../api/antares/services/user.service';
import {GymSettingsResponse} from '../../../api/antares/models/gym.model';
import {MembershipResponse} from '../../../api/antares/models/membership.model';
import {AccessService} from '../../../core/services/access.service';
import {NotificationService} from '../../../core/services/notification.service';
import {problemDetailOf} from '../../../core/models/problem-detail.model';
import {PageComponent} from '../../../shared/component/page/page.component';
import {FormFieldComponent} from '../../../shared/component/form-field/form-field.component';
import {ConfirmDialogComponent} from '../../../shared/component/confirm-dialog/confirm-dialog.component';
import {NativeSelectDirective} from '../../../shared/component/native-select/native-select.directive';
import {StaffGymPickerComponent, staffGymSelection} from '../staff-gym';

/** Sensitive action waiting for confirmation. */
type SettingsDialog = 'regenerate' | 'transfer' | 'delete';

/**
 * My box › Settings (MANAGE_SETTINGS): name and description, enrollment code (copy, edit,
 * regenerate), invitation link, auto-subscription; for the Owner/Programmer, the danger zone
 * (ownership transfer to a member Antares marks `canBecomeHolder`, deletion).
 */
@Component({
  selector: 'app-box-settings',
  standalone: true,
  imports: [ReactiveFormsModule, TranslatePipe, NgIcon, HlmAlertImports, HlmButton, HlmCardImports, HlmInputGroupImports,
    HlmInput, HlmLabel, HlmSpinner, HlmSwitchImports, HlmTextarea, PageComponent, FormFieldComponent, ConfirmDialogComponent,
    NativeSelectDirective, StaffGymPickerComponent],
  templateUrl: './box-settings.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BoxSettingsComponent {
  // --- INJECTIONS ---
  private readonly gymService = inject(GymService);
  private readonly membershipService = inject(MembershipService);
  private readonly userService = inject(UserService);
  private readonly accessService = inject(AccessService);
  private readonly notificationService = inject(NotificationService);
  private readonly translate = inject(TranslateService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);
  private readonly document = inject(DOCUMENT);

  // --- STATE (SIGNALS & VARIABLES) ---
  readonly staffGym = staffGymSelection('MANAGE_SETTINGS');
  readonly settings = signal<GymSettingsResponse | null>(null);
  readonly isLoading = signal<boolean>(false);
  readonly isSaving = signal<boolean>(false);
  readonly transferCandidates = signal<MembershipResponse[]>([]);
  readonly transferTo = signal<number | null>(null);
  readonly dialog = signal<SettingsDialog | null>(null);
  readonly deleteConfirmation = signal<string>('');

  readonly identityForm = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    description: ['', [Validators.maxLength(1000)]]
  });
  readonly enrollmentForm = this.fb.nonNullable.group({
    enrollmentCode: ['', [Validators.required, Validators.minLength(4), Validators.maxLength(50)]],
    isAutoSubscription: [false]
  });

  // --- COMPUTED STATE ---
  /** Owner/Programmer of the managed gym, or platform admin: transfer and deletion (Antares isGymHolder). */
  readonly isHolder = computed(() => {
    const role = this.staffGym.membership()?.gymRole;
    return this.accessService.isAdmin() || role === 'OWNER' || role === 'PROGRAMMER';
  });
  readonly inviteLink = computed(() => {
    const s = this.settings();
    if (!s) return '';
    return `${this.document.location.origin}/join?gym=${s.id}&code=${encodeURIComponent(s.enrollmentCode)}`;
  });
  readonly transferTarget = computed(() => this.transferCandidates().find(m => m.id === this.transferTo()) ?? null);
  readonly canConfirmDelete = computed(() => this.deleteConfirmation().trim() === this.settings()?.name);

  // --- LIFECYCLE ---
  constructor() {
    effect(() => {
      const gymId = this.staffGym.gymId();
      untracked(() => this.load(gymId));
    });
  }

  // --- DATA FETCHING ---
  load(gymId: number | null): void {
    this.settings.set(null);
    this.transferCandidates.set([]);
    this.transferTo.set(null);
    if (gymId === null) return;
    this.isLoading.set(true);
    this.gymService.getSettings(gymId).subscribe({
      next: settings => {
        this.isLoading.set(false);
        this.applySettings(settings);
      },
      error: err => {
        this.isLoading.set(false);
        this.showError(err);
      }
    });
    if (this.isHolder()) {
      this.membershipService.getMemberships(gymId, 'ACTIVE').subscribe({
        next: members => this.transferCandidates.set(members.filter(m => m.capabilities?.canBecomeHolder)),
        error: () => this.transferCandidates.set([])
      });
    }
  }

  // --- ACTIONS & EVENTS ---
  saveIdentity(): void {
    const s = this.settings();
    if (!s || this.identityForm.invalid) {
      this.identityForm.markAllAsTouched();
      return;
    }
    const {name, description} = this.identityForm.getRawValue();
    this.save(this.gymService.updateGym(s.id, {name: name.trim(), description: description.trim() || null}), true);
  }

  saveEnrollment(): void {
    const s = this.settings();
    if (!s || this.enrollmentForm.invalid) {
      this.enrollmentForm.markAllAsTouched();
      return;
    }
    const {enrollmentCode, isAutoSubscription} = this.enrollmentForm.getRawValue();
    this.save(this.gymService.updateSettings(s.id, {enrollmentCode: enrollmentCode.trim(), isAutoSubscription}));
  }

  async copy(text: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(text);
      this.notificationService.showSuccess(this.translate.instant('GLOBAL.COPIED'));
    } catch {
      this.notificationService.showError(this.translate.instant('GLOBAL.ERROR_UNEXPECTED'));
    }
  }

  onTransferTo(value: string): void {
    this.transferTo.set(value ? Number(value) : null);
  }

  openDialog(dialog: SettingsDialog): void {
    this.deleteConfirmation.set('');
    this.dialog.set(dialog);
  }

  closeDialog(): void {
    this.dialog.set(null);
  }

  confirmDialog(): void {
    const s = this.settings();
    const dialog = this.dialog();
    this.dialog.set(null);
    if (!s) return;
    if (dialog === 'regenerate') {
      this.save(this.gymService.regenerateEnrollmentCode(s.id), false, 'BOX.SETTINGS.REGENERATED');
    } else if (dialog === 'transfer') {
      this.transfer(s);
    } else if (dialog === 'delete' && this.canConfirmDelete()) {
      this.delete(s);
    }
  }

  // --- HELPERS ---
  fullName(member: MembershipResponse | null): string {
    return member ? `${member.user.firstName} ${member.user.lastName}` : '';
  }

  private transfer(s: GymSettingsResponse): void {
    const target = this.transferTarget();
    if (!target) return;
    this.isSaving.set(true);
    this.gymService.transferOwnership(s.id, target.id).subscribe({
      next: () => {
        this.isSaving.set(false);
        this.notificationService.showSuccess(
          this.translate.instant('BOX.SETTINGS.TRANSFERRED', {gym: s.name, name: this.fullName(target)}));
        // The former holder may lose MANAGE_SETTINGS: refresh rights, then leave the page
        this.userService.refreshCurrentUser().subscribe(() => void this.router.navigate(['/my-account/gyms']));
      },
      error: err => {
        this.isSaving.set(false);
        this.showError(err);
      }
    });
  }

  private delete(s: GymSettingsResponse): void {
    this.isSaving.set(true);
    this.gymService.deleteGym(s.id).subscribe({
      next: () => {
        this.isSaving.set(false);
        this.notificationService.showSuccess(this.translate.instant('BOX.SETTINGS.DELETED', {name: s.name}));
        this.userService.refreshCurrentUser().subscribe(() => void this.router.navigate(['/my-account/gyms']));
      },
      error: err => {
        this.isSaving.set(false);
        this.showError(err);
      }
    });
  }

  /** Saves, applies the answer and, after a rename, refreshes the user (gym names in memberships). */
  private save(request$: Observable<GymSettingsResponse>, refreshUser = false,
               successKey = 'BOX.SETTINGS.SAVED'): void {
    this.isSaving.set(true);
    request$.subscribe({
      next: settings => {
        this.isSaving.set(false);
        this.applySettings(settings);
        this.notificationService.showSuccess(this.translate.instant(successKey));
        if (refreshUser) this.userService.refreshCurrentUser().subscribe();
      },
      error: err => {
        this.isSaving.set(false);
        this.showError(err);
      }
    });
  }

  private applySettings(settings: GymSettingsResponse): void {
    this.settings.set(settings);
    this.identityForm.reset({name: settings.name, description: settings.description ?? ''});
    this.enrollmentForm.reset({enrollmentCode: settings.enrollmentCode, isAutoSubscription: settings.isAutoSubscription});
  }

  private showError(err: unknown): void {
    this.notificationService.showError(problemDetailOf(err) ?? this.translate.instant('GLOBAL.ERROR_UNEXPECTED'));
  }
}
