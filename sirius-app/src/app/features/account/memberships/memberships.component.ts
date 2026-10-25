import {ChangeDetectionStrategy, Component, computed, inject} from '@angular/core';
import {TranslatePipe} from '@ngx-translate/core';
import {NgIcon} from '@ng-icons/core';
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
  imports: [TranslatePipe, NgIcon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="card bg-base-100">
      <div class="card-body">
        <h2 class="card-title flex gap-2 mb-2">
          <ng-icon name="heroBuildingOffice" class="text-2xl"></ng-icon>
          {{ 'PROFILE.GYMS.TITLE' | translate }}
        </h2>

        @for (m of memberships(); track m.gymId) {
          <div class="border border-base-300 rounded-box p-4 flex flex-col gap-3">
            <div class="flex flex-wrap items-center gap-2">
              <span class="font-semibold text-lg">{{ m.gymName }}</span>
              <span class="badge badge-primary badge-soft">{{ 'GYM.ROLES.' + m.gymRole | translate }}</span>
              @if (m.status !== 'ACTIVE') {
                <span class="badge badge-warning badge-soft">{{ 'GYM.STATUS.' + m.status | translate }}</span>
              }
              @if (m.gymStatus !== 'ACTIVE') {
                <span class="badge badge-ghost">{{ 'GYM.GYM_STATUS.' + m.gymStatus | translate }}</span>
              }
            </div>

            @if (m.permissions.length > 0) {
              <ul class="flex flex-col gap-2">
                @for (permission of m.permissions; track permission) {
                  <li class="flex items-start gap-2">
                    <span class="badge badge-secondary badge-soft shrink-0">
                      {{ 'GYM.PERMISSIONS.' + permission + '.LABEL' | translate }}
                    </span>
                    <span class="text-sm opacity-80">{{ 'GYM.PERMISSIONS.' + permission + '.DESCRIPTION' | translate }}</span>
                  </li>
                }
              </ul>
            } @else {
              <p class="text-sm opacity-70">{{ 'PROFILE.GYMS.NO_PERMISSION' | translate }}</p>
            }
          </div>
        } @empty {
          <p class="opacity-70">{{ 'PROFILE.GYMS.NONE' | translate }}</p>
        }
      </div>
    </div>
  `
})
export class MembershipsComponent {
  private readonly authService = inject(AuthService);

  readonly memberships = computed(() => this.authService.currentUser()?.memberships ?? []);
}
