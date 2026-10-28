import {ChangeDetectionStrategy, Component, computed, inject, signal} from '@angular/core';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {NgIcon} from '@ng-icons/core';
import {HlmAlertImports} from '@spartan-ng/helm/alert';
import {HlmButton} from '@spartan-ng/helm/button';
import {AuthService} from '../../../api/antares/services/auth.service';
import {UserService} from '../../../api/antares/services/user.service';
import {NotificationService} from '../../../core/services/notification.service';
import {problemDetailOf} from '../../../core/models/problem-detail.model';

/**
 * Tells an unverified user that joining or requesting a box/prog needs a confirmed address (Antares
 * answers 403 EMAIL_NOT_VERIFIED otherwise), with a button to mail a new link. Shows nothing once
 * the address is verified.
 */
@Component({
  selector: 'app-email-verification-notice',
  standalone: true,
  imports: [TranslatePipe, NgIcon, HlmAlertImports, HlmButton],
  template: `
    @if (unverified()) {
      <div hlmAlert class="mb-4 border-warning/40" data-slot="email-verification-notice">
        <!-- ng-icon's host style wins over text-* utilities: colour through its input -->
        <ng-icon name="heroEnvelope" color="var(--color-warning)" aria-hidden="true"/>
        <p hlmAlertTitle>{{ 'EMAIL_VERIFICATION.TITLE' | translate }}</p>
        <div hlmAlertDescription class="flex flex-col gap-2">
          <p>{{ 'EMAIL_VERIFICATION.TEXT' | translate: {email: email()} }}</p>
          <button hlmBtn variant="outline" size="sm" type="button" class="self-start" [disabled]="sending()"
                  (click)="resend()">{{ 'PROFILE.RESEND' | translate }}</button>
        </div>
      </div>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmailVerificationNoticeComponent {
  // --- INJECTIONS ---
  private readonly authService = inject(AuthService);
  private readonly userService = inject(UserService);
  private readonly notificationService = inject(NotificationService);
  private readonly translate = inject(TranslateService);

  // --- STATE (SIGNALS & VARIABLES) ---
  readonly sending = signal(false);

  // --- COMPUTED STATE ---
  readonly unverified = computed(() => this.authService.currentUser()?.emailVerified === false);
  readonly email = computed(() => this.authService.currentUser()?.email ?? '');

  // --- ACTIONS & EVENTS ---
  resend(): void {
    this.sending.set(true);
    this.userService.resendVerification().subscribe({
      next: () => {
        this.sending.set(false);
        this.notificationService.showSuccess(this.translate.instant('PROFILE.RESEND_DONE'));
      },
      error: err => {
        this.sending.set(false);
        this.notificationService.showError(problemDetailOf(err) ?? this.translate.instant('GLOBAL.ERROR_UNEXPECTED'));
      }
    });
  }
}
