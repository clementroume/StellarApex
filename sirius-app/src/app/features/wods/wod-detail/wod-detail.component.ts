import {ChangeDetectionStrategy, Component, computed, effect, inject, input, signal} from '@angular/core';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {Router, RouterModule} from '@angular/router';
import {Location, NgTemplateOutlet} from '@angular/common';
import {HttpErrorResponse} from '@angular/common/http';
import {NgIconComponent} from '@ng-icons/core';
import {WodMovementResponse, WodResponse} from '../../../api/aldebaran/models/wod.model';
import {WodService} from '../../../api/aldebaran/services/wod.service';
import {NotificationService} from '../../../core/services/notification.service';
import {AccessService} from '../../../core/services/access.service';
import {ProblemDetail} from '../../../core/models/problem-detail.model';
import {
  DetailAdminActionsComponent
} from '../../../shared/component/detail/detail-admin-actions/detail-admin-actions.component';
import {DetailCardComponent} from '../../../shared/component/detail/detail-card/detail-card.component';
import {DetailHeaderComponent} from '../../../shared/component/detail/detail-header/detail-header.component';
import {DetailStateComponent} from '../../../shared/component/detail/detail-state/detail-state.component';
import {formatDuration} from '../wod.utils';

export type WodVisibility = 'PUBLIC' | 'GYM' | 'PRIVATE';

@Component({
  selector: 'app-wod-detail',
  standalone: true,
  imports: [TranslatePipe, RouterModule, NgIconComponent, NgTemplateOutlet, DetailAdminActionsComponent, DetailCardComponent, DetailHeaderComponent, DetailStateComponent],
  templateUrl: './wod-detail.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class WodDetailComponent {
  // --- INJECTIONS ---
  id = input.required<string>();
  private readonly translate = inject(TranslateService);
  private readonly wodService = inject(WodService);
  private readonly accessService = inject(AccessService);
  private readonly notificationService = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly location = inject(Location);

  // --- STATE (SIGNALS) ---
  wod = signal<WodResponse | null>(null);
  isLoading = signal<boolean>(true);

  // --- COMPUTED STATE ---
  sortedMovements = computed<WodMovementResponse[]>(() =>
    [...(this.wod()?.movements ?? [])].sort((a, b) => a.orderIndex - b.orderIndex)
  );

  visibility = computed<WodVisibility | null>(() => {
    const wod = this.wod();
    if (!wod) return null;
    if (wod.isPublic) return 'PUBLIC';
    return wod.gymId == null ? 'PRIVATE' : 'GYM';
  });

  /** Computed by Aldebaran (WodSecurity.canModify): a locked WOD cannot be edited nor deleted. */
  canModify = computed(() => {
    const capabilities = this.wod()?.capabilities;
    return !!capabilities?.canModify && !capabilities.locked;
  });

  isLocked = computed(() => !!this.wod()?.capabilities?.locked);

  gymName = computed(() => {
    const gymId = this.wod()?.gymId;
    return this.accessService.memberships().find(m => m.gymId === gymId)?.gymName ?? null;
  });

  constructor() {
    effect(() => {
      const currentId = Number(this.id());
      if (currentId && !Number.isNaN(currentId)) {
        this.loadWod(currentId);
      }
    });
  }

  // --- DATA FETCHING ---
  private loadWod(id: number): void {
    this.isLoading.set(true);

    this.wodService.getWod(id).subscribe({
      next: (w) => {
        this.wod.set(w);
        this.isLoading.set(false);
      },
      error: () => {
        this.notificationService.showError(this.translate.instant('WOD.MESSAGES.NOT_FOUND'));
        this.isLoading.set(false);
        this.location.back();
      }
    });
  }

  // --- ACTIONS ---
  goBack(): void {
    this.location.back();
  }

  onDelete(wod: WodResponse): void {
    const confirmMsg = this.translate.instant('GLOBAL.CONFIRM_DELETE', {name: wod.title});

    if (confirm(confirmMsg)) {
      this.wodService.deleteWod(wod.id).subscribe({
        next: () => {
          this.notificationService.showSuccess(this.translate.instant('GLOBAL.DELETE_SUCCESS'));
          this.wodService.notifyRefresh();
          void this.router.navigate(['/wods']);
        },
        error: (err: HttpErrorResponse) => {
          if (err.status === 409) {
            const problem: ProblemDetail = err.error;
            this.notificationService.showError(problem?.detail || this.translate.instant('WOD.MESSAGES.LOCKED'));
          } else if (err.status === 403) {
            this.notificationService.showError(this.translate.instant('WOD.MESSAGES.FORBIDDEN'));
          } else {
            this.notificationService.showError(this.translate.instant('GLOBAL.DELETE_ERROR'));
          }
        }
      });
    }
  }

  // --- HELPERS ---
  formatDuration(seconds?: number): string {
    return formatDuration(seconds);
  }
}
