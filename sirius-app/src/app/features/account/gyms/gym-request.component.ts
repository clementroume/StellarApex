import {ChangeDetectionStrategy, Component, computed, inject, signal} from '@angular/core';
import {Router, RouterLink} from '@angular/router';
import {FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {NgIcon} from '@ng-icons/core';
import {HlmAlertImports} from '@spartan-ng/helm/alert';
import {HlmButton} from '@spartan-ng/helm/button';
import {HlmCardImports} from '@spartan-ng/helm/card';
import {HlmInputGroupImports} from '@spartan-ng/helm/input-group';
import {HlmSpinner} from '@spartan-ng/helm/spinner';
import {HlmTextarea} from '@spartan-ng/helm/textarea';
import {HlmLabel} from '@spartan-ng/helm/label';
import {AuthService} from '../../../api/antares/services/auth.service';
import {GymService} from '../../../api/antares/services/gym.service';
import {UserService} from '../../../api/antares/services/user.service';
import {NotificationService} from '../../../core/services/notification.service';
import {problemDetailOf} from '../../../core/models/problem-detail.model';
import {FormFieldComponent} from '../../../shared/component/form-field/form-field.component';

/**
 * Asks for a new box or program (D1): open to every user, approved by a platform admin, one request
 * waiting at a time (Antares answers 409 otherwise; the form only says it beforehand).
 */
@Component({
  selector: 'app-gym-request',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe, NgIcon, HlmAlertImports, HlmButton, HlmCardImports,
    HlmInputGroupImports, HlmSpinner, HlmTextarea, HlmLabel, FormFieldComponent],
  templateUrl: './gym-request.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GymRequestComponent {
  // --- INJECTIONS ---
  private readonly authService = inject(AuthService);
  private readonly gymService = inject(GymService);
  private readonly userService = inject(UserService);
  private readonly notificationService = inject(NotificationService);
  private readonly translate = inject(TranslateService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  // --- STATE (SIGNALS & VARIABLES) ---
  readonly isSending = signal<boolean>(false);
  readonly form = this.fb.nonNullable.group({
    isProgramming: [false],
    name: ['', [Validators.required, Validators.maxLength(100)]],
    description: ['', [Validators.maxLength(1000)]]
  });

  // --- COMPUTED STATE ---
  /** The user's box/prog still waiting for admin approval, if any. */
  readonly pendingRequest = computed(() =>
    (this.authService.currentUser()?.memberships ?? []).find(m =>
      m.gymStatus === 'PENDING_APPROVAL' && (m.gymRole === 'OWNER' || m.gymRole === 'PROGRAMMER')
    ) ?? null
  );

  // --- ACTIONS & EVENTS ---
  setType(isProgramming: boolean): void {
    this.form.controls.isProgramming.setValue(isProgramming);
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const {name, description, isProgramming} = this.form.getRawValue();
    this.isSending.set(true);
    this.gymService.createGym({name: name.trim(), description: description.trim() || undefined, isProgramming}).subscribe({
      next: () => {
        this.isSending.set(false);
        this.notificationService.showSuccess(this.translate.instant('CREATE_GYM.SENT'));
        this.userService.refreshCurrentUser().subscribe();
        void this.router.navigate(['/my-account/gyms']);
      },
      error: err => {
        this.isSending.set(false);
        this.notificationService.showError(problemDetailOf(err) ?? this.translate.instant('GLOBAL.ERROR_UNEXPECTED'));
      }
    });
  }

  // --- HELPERS ---
  nameError(): string | null {
    const control = this.form.controls.name;
    if (!control.touched || control.valid) return null;
    return this.translate.instant(control.hasError('maxlength') ? 'CREATE_GYM.ERROR_NAME_MAX' : 'GLOBAL.REQUIRED');
  }
}
