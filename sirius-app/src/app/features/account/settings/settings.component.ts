import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AbstractControl, FormBuilder, FormGroup, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { AuthService } from '../../../core/services/auth.service';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NotificationService } from '../../../core/services/notification.service';
import { HttpErrorResponse } from '@angular/common/http';
import { ThemeService } from '../../../core/services/theme.service';
import { PreferencesUpdateRequest } from '../../../core/models/user.model';

/**
 * Custom validator to check if the new password and confirmation password fields match.
 */
export const passwordMatchValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const newPassword = control.get('newPassword');
  const confirmationPassword = control.get('confirmationPassword');
  return newPassword && confirmationPassword && newPassword.value !== confirmationPassword.value ? { passwordsMismatch: true } : null;
};

/**
 * Component for managing user security (password change) and preferences (language).
 */
@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, TranslateModule],
  templateUrl: './settings.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SettingsComponent {
  public readonly passwordForm: FormGroup;

  private readonly authService = inject(AuthService);
  private readonly fb = inject(FormBuilder);
  private readonly notificationService = inject(NotificationService);
  private readonly themeService = inject(ThemeService);
  public readonly translate = inject(TranslateService);

  constructor() {
    this.passwordForm = this.fb.group({
      currentPassword: ['', [Validators.required]],
      newPassword: ['', [Validators.required, Validators.minLength(8)]],
      confirmationPassword: ['', [Validators.required]]
    }, { validators: passwordMatchValidator });
  }

  /**
   * Handles the password change form submission.
   */
  onSubmitPassword(): void {
    if (this.passwordForm.invalid) {
      return;
    }

    this.authService.changePassword(this.passwordForm.value).subscribe({
      next: () => {
        this.translate.get('SETTINGS.SUCCESS_PASSWORD_UPDATE').subscribe((message: string) => {
          this.notificationService.showSuccess(message);
        });
        this.passwordForm.reset();
      },
      error: (err: HttpErrorResponse) => {
        if (err.error?.message) {
          this.notificationService.showError(err.error.message);
        }
      }
    });
  }

  /**
   * Handles language selection change and persists it to the backend.
   */
  changeLanguage(event: Event): void {
    const lang = (event.target as HTMLSelectElement).value;
    this.translate.use(lang);

    const currentUser = this.authService.currentUser();
    if (currentUser) {
      const preferences: PreferencesUpdateRequest = {
        locale: lang as 'en' | 'fr',
        theme: currentUser.theme
      };

      this.authService.updatePreferences(preferences).subscribe({
        error: (err) => {
          console.error('Failed to update language preference:', err);
          // Revert to the previous language on failure.
          this.translate.use(currentUser.locale);
        }
      });
    }
  }
}
