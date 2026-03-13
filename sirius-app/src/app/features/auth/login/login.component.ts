import {ChangeDetectionStrategy, Component, DOCUMENT, Inject, inject, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormBuilder, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {ActivatedRoute, Router, RouterModule} from '@angular/router';
import {AuthService} from '../../../api/antares/services/auth.service';
import {TranslateModule} from '@ngx-translate/core';
import {NotificationService} from '../../../core/services/notification.service';
import {HttpErrorResponse} from '@angular/common/http';
import {ProblemDetail} from '../../../core/models/problem-detail.model';
import {NgIcon} from '@ng-icons/core';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule, TranslateModule, NgIcon],
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

  private returnUrl = '/dashboard';

  constructor(@Inject(DOCUMENT) private readonly document: Document) {
    this.loginForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required]]
    });
  }

  ngOnInit(): void {
    this.returnUrl = this.route.snapshot.queryParams['returnUrl'] || '/dashboard';
  }

  onSubmit(): void {
    if (this.loginForm.invalid) {
      return;
    }

    this.authService.login(this.loginForm.value).subscribe({
      next: () => {
        // Check if the returnUrl is an external absolute URL (starts with http)
        if (this.returnUrl.startsWith('http')) {
          // Native browser redirect for external domains (Admin, Traefik)
          this.redirectToExternal(this.returnUrl);
        } else {
          // Standard Angular routing for internal pages
          void this.router.navigate([this.returnUrl]);
        }
      },
      error: (err: HttpErrorResponse) => {
        const problem: ProblemDetail = err.error;
        const message = problem?.detail || problem?.title || 'Une erreur est survenue';
        this.notificationService.showError(message);
      }
    });
  }

  public redirectToExternal(url: string): void {
    this.document.location.href = url;
  }
}
