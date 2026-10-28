import {ChangeDetectionStrategy, Component, DOCUMENT, inject} from '@angular/core';
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
import {ActivatedRoute, Router, RouterModule} from '@angular/router';
import {AuthService} from '../../../api/antares/services/auth.service';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {NotificationService} from '../../../core/services/notification.service';
import {HttpErrorResponse} from '@angular/common/http';
import {ProblemDetail} from '../../../core/models/problem-detail.model';
import {HlmButton} from '@spartan-ng/helm/button';
import {HlmCardImports} from '@spartan-ng/helm/card';
import {HlmInputGroupImports} from '@spartan-ng/helm/input-group';
import {FormFieldComponent} from '../../../shared/component/form-field/form-field.component';
import {forwardableReturnUrl, resolveReturnUrl} from '../return-url';

export const passwordMatchValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const password = control.get('password');
  const confirmation = control.get('confirmationPassword');
  return password && confirmation && password.value !== confirmation.value
    ? {passwordsMismatch: true}
    : null;
};

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule, TranslatePipe, HlmButton, HlmCardImports,
    HlmInputGroupImports, FormFieldComponent],
  templateUrl: './register.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegisterComponent {
  public readonly registerForm: FormGroup;
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);
  private readonly notificationService = inject(NotificationService);
  private readonly document = inject(DOCUMENT);
  private readonly translate = inject(TranslateService);

  /** Checked destination after sign-up (e.g. the join step of an invitation link). */
  private readonly target = resolveReturnUrl(
    this.route.snapshot.queryParams['returnUrl'], this.document.location.hostname);
  /** App path handed back to the sign-in page. */
  protected readonly forwardReturnUrl = forwardableReturnUrl(
    this.route.snapshot.queryParams['returnUrl'], this.document.location.hostname);

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

  onSubmit(): void {
    if (this.registerForm.invalid) {
      return;
    }

    const {confirmationPassword, ...form} = this.registerForm.value;
    // The account (and its verification e-mail) starts in the language shown
    const lang = this.translate.currentLang() ?? this.translate.fallbackLang();
    const registerPayload = {...form, ...(lang ? {locale: lang} : {})};

    this.authService.register(registerPayload).subscribe({
      next: () => {
        this.notificationService.showSuccess(
          this.translate.instant('REGISTER.CHECK_EMAIL', {email: registerPayload.email}));
        if (this.target.kind === 'external') {
          this.redirectToExternal(this.target.url);
        } else {
          void this.router.navigateByUrl(this.target.url);
        }
      },
      error: (err: HttpErrorResponse) => {
        const problem: ProblemDetail = err.error;
        const message = problem?.detail || problem?.title || 'Une erreur est survenue';
        this.notificationService.showError(message);
      }
    });
  }

  /** A control's error is shown once the user has left it. */
  protected invalid(name: string): boolean {
    const control = this.registerForm.get(name);
    return !!control && control.invalid && control.touched;
  }

  protected passwordsMismatch(): boolean {
    return !!this.registerForm.errors?.['passwordsMismatch'] && !!this.registerForm.get('confirmationPassword')?.touched;
  }

  public redirectToExternal(url: string): void {
    this.document.location.href = url;
  }
}
