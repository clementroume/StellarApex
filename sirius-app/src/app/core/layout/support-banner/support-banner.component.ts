import {ChangeDetectionStrategy, Component, computed, DestroyRef, effect, inject, signal} from '@angular/core';
import {TranslatePipe} from '@ngx-translate/core';
import {NgIcon} from '@ng-icons/core';
import {AuthService} from '../../../api/antares/services/auth.service';
import {SupportSessionService} from '../../services/support-session.service';
import {formatDuration} from '../../../features/wods/wod.utils';

@Component({
  selector: 'app-support-banner',
  standalone: true,
  imports: [TranslatePipe, NgIcon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (supportSessionService.session(); as session) {
      <div class="bg-warning text-warning-content px-4 py-2 flex flex-col md:flex-row items-center justify-center gap-2 md:gap-4 text-sm">
        <span class="flex items-center gap-2 font-semibold">
          <ng-icon name="heroExclamationTriangle" size="1.2rem"></ng-icon>
          {{ 'ADMIN.SUPPORT.BANNER' | translate: {name: targetName(), actor: session.actor.firstName + ' ' + session.actor.lastName} }}
        </span>
        <span class="opacity-80">{{ 'ADMIN.SUPPORT.REMAINING' | translate: {time: remaining()} }}</span>
        <button type="button" class="btn btn-xs" [disabled]="isEnding()" (click)="end()">
          {{ 'ADMIN.SUPPORT.END' | translate }}
        </button>
      </div>
    }
  `
})
export class SupportBannerComponent {
  // --- INJECTIONS ---
  readonly supportSessionService = inject(SupportSessionService);
  private readonly authService = inject(AuthService);

  // --- STATE ---
  private readonly now = signal(Date.now());
  isEnding = signal(false);

  // --- COMPUTED STATE ---
  targetName = computed(() => {
    const user = this.authService.currentUser();
    return user ? `${user.firstName} ${user.lastName}` : '';
  });

  private readonly remainingSeconds = computed(() => {
    const session = this.supportSessionService.session();
    if (!session) return 0;
    return Math.max(0, Math.floor((new Date(session.expiresAt).getTime() - this.now()) / 1000));
  });

  remaining = computed(() => formatDuration(this.remainingSeconds()));

  constructor() {
    const timer = setInterval(() => this.now.set(Date.now()), 1000);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));

    // The server rejects an expired token anyway; this only brings the admin back promptly
    effect(() => {
      if (this.supportSessionService.isActive() && this.remainingSeconds() === 0) {
        this.supportSessionService.handleExpired();
      }
    });
  }

  // --- ACTIONS ---
  end(): void {
    this.isEnding.set(true);
    this.supportSessionService.end().subscribe(() => this.isEnding.set(false));
  }
}
