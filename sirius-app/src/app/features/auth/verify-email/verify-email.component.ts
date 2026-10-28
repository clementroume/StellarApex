import {ChangeDetectionStrategy, Component, computed, DOCUMENT, inject, OnInit, signal} from '@angular/core';
import {RouterModule} from '@angular/router';
import {TranslatePipe} from '@ngx-translate/core';
import {NgIcon} from '@ng-icons/core';
import {HlmButton} from '@spartan-ng/helm/button';
import {HlmCardImports} from '@spartan-ng/helm/card';
import {AuthService} from '../../../api/antares/services/auth.service';
import {UserService} from '../../../api/antares/services/user.service';
import {problemDetailOf} from '../../../core/models/problem-detail.model';
import {readLinkToken} from '../auth-token';

type VerifyState = 'verifying' | 'verified' | 'failed';

/**
 * Confirms the address from a mailed link (`/auth/verify-email#token=...`). Works signed out (link
 * opened on another device); a signed-in user is refreshed so the UI unlocks joining a box/prog.
 */
@Component({
  selector: 'app-verify-email',
  standalone: true,
  imports: [RouterModule, TranslatePipe, NgIcon, HlmButton, HlmCardImports],
  templateUrl: './verify-email.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VerifyEmailComponent implements OnInit {
  // --- INJECTIONS ---
  private readonly authService = inject(AuthService);
  private readonly userService = inject(UserService);
  private readonly document = inject(DOCUMENT);

  // --- STATE (SIGNALS & VARIABLES) ---
  readonly state = signal<VerifyState>('verifying');
  readonly error = signal<string | null>(null);

  // --- COMPUTED STATE ---
  readonly signedIn = computed(() => this.authService.isAuthenticated());

  // --- LIFECYCLE ---
  ngOnInit(): void {
    const token = readLinkToken(this.document);
    if (!token) {
      this.state.set('failed');
      return;
    }
    this.authService.verifyEmail({token}).subscribe({
      next: () => {
        this.state.set('verified');
        // Link opened in a new tab: the user is not loaded yet; a signed-in user gets the new flag
        if (this.authService.currentUser() === undefined) {
          this.authService.initCurrentUser().subscribe();
        } else if (this.signedIn()) {
          this.userService.refreshCurrentUser().subscribe({error: () => undefined});
        }
      },
      error: err => {
        this.error.set(problemDetailOf(err));
        this.state.set('failed');
      }
    });
  }
}
