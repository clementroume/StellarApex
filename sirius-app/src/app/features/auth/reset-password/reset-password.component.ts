import {ChangeDetectionStrategy, Component, DOCUMENT, inject, OnInit, signal} from '@angular/core';
import {FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {Router, RouterModule} from '@angular/router';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {NgIcon} from '@ng-icons/core';
import {HlmButton} from '@spartan-ng/helm/button';
import {HlmCardImports} from '@spartan-ng/helm/card';
import {HlmInputGroupImports} from '@spartan-ng/helm/input-group';
import {AuthService} from '../../../api/antares/services/auth.service';
import {NotificationService} from '../../../core/services/notification.service';
import {problemDetailOf} from '../../../core/models/problem-detail.model';
import {FormFieldComponent} from '../../../shared/component/form-field/form-field.component';
import {passwordMatchValidator} from '../register/register.component';
import {readLinkToken} from '../auth-token';

/**
 * New password from a mailed reset link (`/auth/reset-password#token=...`). Antares ends every
 * session of the account, so the user signs in again with the new password.
 */
@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [ReactiveFormsModule, RouterModule, TranslatePipe, NgIcon, HlmButton, HlmCardImports,
    HlmInputGroupImports, FormFieldComponent],
  templateUrl: './reset-password.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ResetPasswordComponent implements OnInit {
  // --- INJECTIONS ---
  private readonly authService = inject(AuthService);
  private readonly notificationService = inject(NotificationService);
  private readonly translate = inject(TranslateService);
  private readonly router = inject(Router);
  private readonly document = inject(DOCUMENT);
  private readonly fb = inject(FormBuilder);

  // --- STATE (SIGNALS & VARIABLES) ---
  readonly form = this.fb.nonNullable.group({
    password: ['', [Validators.required, Validators.minLength(8),
      Validators.pattern(/(?=.*\d)(?=.*[a-z])(?=.*[A-Z]).{8,}/)]],
    confirmationPassword: ['', Validators.required]
  }, {validators: passwordMatchValidator});
  /** Token of the link; null when the link carries none (the page then offers a new link). */
  readonly token = signal<string | null>(null);
  readonly saving = signal(false);

  // --- LIFECYCLE ---
  ngOnInit(): void {
    this.token.set(readLinkToken(this.document));
  }

  // --- ACTIONS & EVENTS ---
  onSubmit(): void {
    const token = this.token();
    if (!token || this.form.invalid || this.saving()) return;
    const {password, confirmationPassword} = this.form.getRawValue();
    this.saving.set(true);
    this.authService.resetPassword({token, newPassword: password, confirmationPassword}).subscribe({
      next: () => {
        // Every session ended server side: forget the user shown here too
        this.authService.updateCurrentUser(null);
        this.notificationService.showSuccess(this.translate.instant('RESET_PASSWORD.SUCCESS'));
        void this.router.navigate(['/auth/login']);
      },
      error: err => {
        this.saving.set(false);
        this.notificationService.showError(problemDetailOf(err) ?? this.translate.instant('GLOBAL.ERROR_UNEXPECTED'));
      }
    });
  }

  // --- HELPERS ---
  protected invalid(name: 'password' | 'confirmationPassword'): boolean {
    const control = this.form.controls[name];
    return control.invalid && control.touched;
  }

  protected passwordsMismatch(): boolean {
    return !!this.form.errors?.['passwordsMismatch'] && this.form.controls.confirmationPassword.touched;
  }
}
