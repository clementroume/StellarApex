import {ChangeDetectionStrategy, Component, computed, inject, linkedSignal, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {
  AbstractControl,
  FormBuilder,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators
} from '@angular/forms';
import {AuthService} from '../../../api/antares/services/auth.service';
import {UserService} from '../../../api/antares/services/user.service';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {NotificationService} from '../../../core/services/notification.service';
import {HttpErrorResponse} from '@angular/common/http';
import {PreferencesUpdateRequest} from '../../../api/antares/models/user.model';
import {ProblemDetail} from '../../../core/models/problem-detail.model';
import {NgIcon} from '@ng-icons/core';
import {PalettePickerComponent} from '../../../shared/component/palette-picker/palette-picker.component';
import {FormFieldComponent} from '../../../shared/component/form-field/form-field.component';
import {HlmButton} from '@spartan-ng/helm/button';
import {HlmCardImports} from '@spartan-ng/helm/card';
import {HlmAlertImports} from '@spartan-ng/helm/alert';
import {HlmAlertDialogImports} from '@spartan-ng/helm/alert-dialog';
import {HlmInputGroupImports} from '@spartan-ng/helm/input-group';
import {HlmLabel} from '@spartan-ng/helm/label';
import {HlmNativeSelectImports} from '@spartan-ng/helm/native-select';
import {HlmSwitchImports} from '@spartan-ng/helm/switch';
import {BrnDialogState} from '@spartan-ng/brain/dialog';

export const passwordMatchValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const newPassword = control.get('newPassword');
  const confirmationPassword = control.get('confirmationPassword');
  return newPassword && confirmationPassword && newPassword.value !== confirmationPassword.value ? {passwordsMismatch: true} : null;
};

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, TranslatePipe, NgIcon, PalettePickerComponent, FormFieldComponent, HlmButton,
    HlmCardImports, HlmAlertImports, HlmAlertDialogImports, HlmInputGroupImports, HlmLabel, HlmNativeSelectImports,
    HlmSwitchImports],
  templateUrl: './settings.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SettingsComponent {
  public readonly passwordForm: FormGroup;
  public readonly translate = inject(TranslateService);
  /** Account deletion confirmation (spartan alert dialog). */
  readonly deleteDialogState = signal<BrnDialogState>('closed');
  private readonly authService = inject(AuthService);
  private readonly userService = inject(UserService);
  private readonly fb = inject(FormBuilder);
  private readonly notificationService = inject(NotificationService);

  /** Antares refuses password changes and account deletion during an admin support session. */
  readonly isSupportSession = computed(() => !!this.authService.currentUser()?.supportSession);
  /** Shown at once on toggle, reverted if Antares refuses the change. */
  readonly publicLeaderboardName = linkedSignal(() => !!this.authService.currentUser()?.publicLeaderboardName);

  constructor() {
    this.passwordForm = this.fb.group({
      currentPassword: ['', [Validators.required]],
      newPassword: ['', [Validators.required, Validators.minLength(8), Validators.pattern(/(?=.*\d)(?=.*[a-z])(?=.*[A-Z]).{8,}/)]],
      confirmationPassword: ['', [Validators.required]]
    }, {validators: passwordMatchValidator});
  }

  onSubmit(): void {
    if (this.passwordForm.invalid) {
      return;
    }

    this.userService.changePassword(this.passwordForm.value).subscribe({
      next: () => {
        this.translate.get('SETTINGS.SUCCESS_PASSWORD_UPDATE').subscribe((message: string) => {
          this.notificationService.showSuccess(message);
        });
        this.passwordForm.reset();
      },
      error: (err: HttpErrorResponse) => {
        const problem: ProblemDetail = err.error;
        const message = problem?.detail || problem?.title || 'Une erreur est survenue';
        this.notificationService.showError(message);
      }
    });
  }

  changeLanguage(lang: string | null | undefined): void {
    if (!lang) return;
    this.translate.use(lang);

    const currentUser = this.authService.currentUser();
    // During a support session the change stays on the admin's screen (Antares refuses to save it)
    if (currentUser && !currentUser.supportSession) {
      const preferences: PreferencesUpdateRequest = {
        locale: lang,
        theme: currentUser.theme,
        // Sent back unchanged: the preferences are replaced as a whole
        unitSystem: currentUser.unitSystem ?? null
      };

      this.userService.updatePreferences(preferences).subscribe({
        error: () => {
          console.error();
          this.translate.use(currentUser.locale);
        }
      });
    }
  }

  /** Consent to show the name on public leaderboards (gym mates always see it). */
  changePublicLeaderboardName(checked: boolean): void {
    const currentUser = this.authService.currentUser();
    if (!currentUser || currentUser.supportSession) return;
    this.publicLeaderboardName.set(checked);
    this.userService.updatePreferences({
      locale: currentUser.locale,
      theme: currentUser.theme,
      unitSystem: currentUser.unitSystem ?? null,
      publicLeaderboardName: checked
    }).subscribe({
      next: () => this.notificationService.showSuccess(this.translate.instant('SETTINGS.PRIVACY_SAVED')),
      error: () => {
        this.publicLeaderboardName.set(!checked);
        this.notificationService.showError(this.translate.instant('SETTINGS.PRIVACY_ERROR'));
      }
    });
  }

  openDeleteConfirmation(): void {
    this.deleteDialogState.set('open');
  }

  closeDeleteConfirmation(): void {
    this.deleteDialogState.set('closed');
  }

  confirmDeleteAccount(): void {
    this.userService.deleteAccount().subscribe({
      next: () => {
        this.closeDeleteConfirmation();
        this.translate.get('SETTINGS.SUCCESS_DELETE').subscribe((msg) => {
          this.notificationService.showSuccess(msg);
        });
      },
      error: (err) => {
        this.closeDeleteConfirmation();
        // Gestion d'erreur standard
        const problem = err.error;
        const message = problem?.detail || 'Erreur lors de la suppression';
        this.notificationService.showError(message);
      }
    });
  }

  /** A control's error is shown once the user has left it. */
  protected invalid(name: string): boolean {
    const control = this.passwordForm.get(name);
    return !!control && control.invalid && control.touched;
  }

  protected passwordsMismatch(): boolean {
    return !!this.passwordForm.errors?.['passwordsMismatch'] && !!this.passwordForm.get('confirmationPassword')?.touched;
  }
}
