import {ChangeDetectionStrategy, Component, computed, ElementRef, inject, ViewChild} from '@angular/core';
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

export const passwordMatchValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const newPassword = control.get('newPassword');
  const confirmationPassword = control.get('confirmationPassword');
  return newPassword && confirmationPassword && newPassword.value !== confirmationPassword.value ? {passwordsMismatch: true} : null;
};

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, TranslatePipe, NgIcon],
  templateUrl: './settings.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SettingsComponent {
  public readonly passwordForm: FormGroup;
  public readonly translate = inject(TranslateService);
  @ViewChild('deleteModal') deleteModal!: ElementRef<HTMLDialogElement>;
  private readonly authService = inject(AuthService);
  private readonly userService = inject(UserService);
  private readonly fb = inject(FormBuilder);
  private readonly notificationService = inject(NotificationService);

  /** Antares refuses password changes and account deletion during an admin support session. */
  readonly isSupportSession = computed(() => !!this.authService.currentUser()?.supportSession);
  readonly publicLeaderboardName = computed(() => !!this.authService.currentUser()?.publicLeaderboardName);

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

  changeLanguage(event: Event): void {
    const lang = (event.target as HTMLSelectElement).value;
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
  changePublicLeaderboardName(event: Event): void {
    const currentUser = this.authService.currentUser();
    if (!currentUser || currentUser.supportSession) return;
    const checked = (event.target as HTMLInputElement).checked;
    this.userService.updatePreferences({
      locale: currentUser.locale,
      theme: currentUser.theme,
      unitSystem: currentUser.unitSystem ?? null,
      publicLeaderboardName: checked
    }).subscribe({
      next: () => this.notificationService.showSuccess(this.translate.instant('SETTINGS.PRIVACY_SAVED')),
      error: () => {
        (event.target as HTMLInputElement).checked = !checked;
        this.notificationService.showError(this.translate.instant('SETTINGS.PRIVACY_ERROR'));
      }
    });
  }

  openDeleteConfirmation(): void {
    this.deleteModal.nativeElement.showModal();
  }

  closeDeleteConfirmation(): void {
    this.deleteModal.nativeElement.close();
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
}
