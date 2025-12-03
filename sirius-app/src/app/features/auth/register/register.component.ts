import {ChangeDetectionStrategy, Component, inject} from '@angular/core';
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
import {Router, RouterModule} from '@angular/router';
import {AuthService} from '../../../core/services/auth.service';
import {TranslateModule} from '@ngx-translate/core';
import {NotificationService} from '../../../core/services/notification.service';
import {HttpErrorResponse} from '@angular/common/http';
import {ProblemDetail} from '../../../core/models/problem-detail.model';

export const passwordMatchValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const password = control.get('password');
  const confirmation = control.get('confirmationPassword');
  return password && confirmation && password.value !== confirmation.value
    ? {passwordsMismatch: true}
    : null;
};

/**
 * Handles the user registration page.
 * It provides a reactive form for new users to create an account.
 */
@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule, TranslateModule],
  templateUrl: './register.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegisterComponent {
  /** The reactive form group that manages the registration form's state and validation. */
  public readonly registerForm: FormGroup;

  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);
  private readonly notificationService = inject(NotificationService);

  constructor() {
    this.registerForm = this.fb.group({
      firstName: ['', Validators.required],
      lastName: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      password: ['', [
        Validators.required,
        Validators.minLength(8),
        Validators.pattern(/(?=.*\d)(?=.*[a-z])(?=.*[A-Z]).{8,}/)
      ]],
      confirmationPassword: ['', Validators.required]
    }, {validators: passwordMatchValidator});
  }

  /**
   * Handles the registration form submission.
   * On success, navigates to the dashboard. On error, displays a notification.
   */
  onSubmit(): void {
    if (this.registerForm.invalid) {
      return;
    }

    const {confirmationPassword, ...registerPayload} = this.registerForm.value;

    this.authService.register(registerPayload).subscribe({
      next: () => {
        void this.router.navigate(['/dashboard']);
      },
      error: (err: HttpErrorResponse) => {
        const problem: ProblemDetail = err.error;
        const message = problem?.detail || problem?.title || 'Une erreur est survenue';
        this.notificationService.showError(message);
      }
    });
  }
}
