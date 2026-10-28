import {ChangeDetectionStrategy, Component, computed, inject} from '@angular/core';
import {TranslatePipe} from '@ngx-translate/core';
import {NgIcon} from '@ng-icons/core';
import {HlmBadge} from '@spartan-ng/helm/badge';
import {HlmCardImports} from '@spartan-ng/helm/card';
import {AuthService} from '../../../api/antares/services/auth.service';

/**
 * The current user's gyms with role, status and effective permissions (as Antares computes them).
 *
 * <p>During an admin support session it shows the target's memberships, which helps diagnose
 * "why can't this member do X" reports.
 */
@Component({
  selector: 'app-memberships',
  standalone: true,
  imports: [TranslatePipe, NgIcon, HlmBadge, HlmCardImports],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section hlmCard>
      <div hlmCardHeader>
        <h2 hlmCardTitle class="flex items-center gap-2 text-xl">
          <ng-icon name="heroBuildingOffice" size="1.5rem" aria-hidden="true"/>
          {{ 'PROFILE.GYMS.TITLE' | translate }}
        </h2>
      </div>
      <div hlmCardContent class="flex flex-col gap-3">
        @for (m of memberships(); track m.gymId) {
          <div class="border rounded-lg p-4 flex flex-col gap-3">
            <div class="flex flex-wrap items-center gap-2">
              <span class="font-semibold text-lg">{{ m.gymName }}</span>
              <span hlmBadge class="bg-primary/15 text-primary">{{ 'GYM.ROLES.' + m.gymRole | translate }}</span>
              @if (m.status !== 'ACTIVE') {
                <span hlmBadge class="bg-warning/15 text-warning">{{ 'GYM.STATUS.' + m.status | translate }}</span>
              }
              @if (m.gymStatus !== 'ACTIVE') {
                <span hlmBadge variant="secondary">{{ 'GYM.GYM_STATUS.' + m.gymStatus | translate }}</span>
              }
            </div>

            @if (m.permissions.length > 0) {
              <ul class="flex flex-col gap-2">
                @for (permission of m.permissions; track permission) {
                  <li class="flex items-start gap-2">
                    <span hlmBadge class="shrink-0 bg-secondary/15 text-secondary">
                      {{ 'GYM.PERMISSIONS.' + permission + '.LABEL' | translate }}
                    </span>
                    <span class="text-sm text-muted-foreground">{{ 'GYM.PERMISSIONS.' + permission + '.DESCRIPTION' | translate }}</span>
                  </li>
                }
              </ul>
            } @else {
              <p class="text-sm text-muted-foreground">{{ 'PROFILE.GYMS.NO_PERMISSION' | translate }}</p>
            }
          </div>
        } @empty {
          <p class="text-muted-foreground">{{ 'PROFILE.GYMS.NONE' | translate }}</p>
        }
      </div>
    </section>
  `
})
export class MembershipsComponent {
  private readonly authService = inject(AuthService);

  readonly memberships = computed(() => this.authService.currentUser()?.memberships ?? []);
}
