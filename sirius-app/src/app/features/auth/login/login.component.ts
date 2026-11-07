import {ChangeDetectionStrategy, Component, DOCUMENT, Inject, inject, OnInit, signal} from '@angular/core';
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

/** Demo accounts offered on the sign-in page (seeded by Antares' DevDataSeeder, roadmap D68). */
export const DEMO_PERSONAS = [
  {key: 'OWNER', email: 'owner.sparta@demo.test'},
  {key: 'COACH', email: 'coach.sparta@demo.test'},
  {key: 'ATHLETE', email: 'athlete.sparta@demo.test'},
  {key: 'PROGRAMMER', email: 'programmer.zeus@demo.test'},
] as const;

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
  /** Shared demo password; null (nothing shown) when the deployment offers no demo accounts. */
  protected readonly demoPassword = signal<string | null>(null);
  protected readonly demoPersonas = DEMO_PERSONAS;

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
    this.authService.demoAccess().subscribe(access => this.demoPassword.set(access?.password ?? null));
  }

  onSubmit(): void {
    if (this.loginForm.invalid) {
      return;
    }

    this.signIn(this.loginForm.value);
  }

  /** One-click sign-in with a demo account. */
  protected signInDemo(email: string): void {
    const password = this.demoPassword();
    if (password) {
      this.signIn({email, password});
    }
  }

  private signIn(credentials: {email: string; password: string}): void {
    this.authService.login(credentials).subscribe({
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
