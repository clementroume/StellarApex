import {ChangeDetectionStrategy, Component, inject, signal} from '@angular/core';
import {FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {RouterModule} from '@angular/router';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {NgIcon} from '@ng-icons/core';
import {HlmButton} from '@spartan-ng/helm/button';
import {HlmCardImports} from '@spartan-ng/helm/card';
import {HlmInputGroupImports} from '@spartan-ng/helm/input-group';
import {AuthService} from '../../../api/antares/services/auth.service';
import {NotificationService} from '../../../core/services/notification.service';
import {problemDetailOf} from '../../../core/models/problem-detail.model';
import {FormFieldComponent} from '../../../shared/component/form-field/form-field.component';

/**
 * "Forgot password": asks Antares to mail a reset link. The confirmation is the same whether or not
 * the address has an account (Antares never tells).
 */
@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [ReactiveFormsModule, RouterModule, TranslatePipe, NgIcon, HlmButton, HlmCardImports,
    HlmInputGroupImports, FormFieldComponent],
  templateUrl: './forgot-password.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ForgotPasswordComponent {
  // --- INJECTIONS ---
  private readonly authService = inject(AuthService);
  private readonly notificationService = inject(NotificationService);
  private readonly translate = inject(TranslateService);
  private readonly fb = inject(FormBuilder);

  // --- STATE (SIGNALS & VARIABLES) ---
  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]]
  });
  readonly sent = signal(false);
  readonly sending = signal(false);

  // --- ACTIONS & EVENTS ---
  onSubmit(): void {
    if (this.form.invalid || this.sending()) return;
    this.sending.set(true);
    this.authService.forgotPassword({email: this.form.getRawValue().email.trim()}).subscribe({
      next: () => {
        this.sending.set(false);
        this.sent.set(true);
      },
      error: err => {
        this.sending.set(false);
        this.notificationService.showError(problemDetailOf(err) ?? this.translate.instant('GLOBAL.ERROR_UNEXPECTED'));
      }
    });
  }

  // --- HELPERS ---
  protected invalid(): boolean {
    const control = this.form.controls.email;
    return control.invalid && control.touched;
  }
}
