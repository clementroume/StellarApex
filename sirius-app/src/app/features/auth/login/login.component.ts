import {ChangeDetectionStrategy, Component, DOCUMENT, Inject, inject, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormBuilder, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {ActivatedRoute, Router, RouterModule} from '@angular/router';
import {AuthService} from '../../../api/antares/services/auth.service';
import {TranslatePipe} from '@ngx-translate/core';
import {NotificationService} from '../../../core/services/notification.service';
import {HttpErrorResponse} from '@angular/common/http';
import {ProblemDetail} from '../../../core/models/problem-detail.model';
import {HlmButton} from '@spartan-ng/helm/button';
import {HlmCardImports} from '@spartan-ng/helm/card';
import {HlmInputGroupImports} from '@spartan-ng/helm/input-group';
import {FormFieldComponent} from '../../../shared/component/form-field/form-field.component';
import {DEFAULT_RETURN_URL, forwardableReturnUrl, resolveReturnUrl, ReturnTarget} from '../return-url';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule, TranslatePipe, HlmButton, HlmCardImports,
    HlmInputGroupImports, FormFieldComponent],
  templateUrl: './login.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginComponent implements OnInit {
  public readonly loginForm: FormGroup;
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);
  private readonly notificationService = inject(NotificationService);

  /** Checked destination after sign-in (see resolveReturnUrl). */
  private target: ReturnTarget = {kind: 'internal', url: DEFAULT_RETURN_URL};
  /** App path handed to the sign-up page, so an invitation link survives creating an account. */
  protected forwardReturnUrl: string | null = null;

  constructor(@Inject(DOCUMENT) private readonly document: Document) {
    this.loginForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required]]
    });
  }

  ngOnInit(): void {
    const raw = this.route.snapshot.queryParams['returnUrl'];
    this.target = resolveReturnUrl(raw, this.document.location.hostname);
    this.forwardReturnUrl = forwardableReturnUrl(raw, this.document.location.hostname);
  }

  onSubmit(): void {
    if (this.loginForm.invalid) {
      return;
    }

    this.authService.login(this.loginForm.value).subscribe({
      next: () => {
        if (this.target.kind === 'external') {
          // Native browser redirect for the admin tools behind Traefik (subdomains of this host)
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
    const control = this.loginForm.get(name);
    return !!control && control.invalid && control.touched;
  }

  public redirectToExternal(url: string): void {
    this.document.location.href = url;
  }
}
