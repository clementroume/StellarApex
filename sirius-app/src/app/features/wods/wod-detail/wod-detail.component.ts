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
import {UnitService} from '../../../core/services/unit.service';
import {
  DetailAdminActionsComponent
} from '../../../shared/component/detail/detail-admin-actions/detail-admin-actions.component';
import {DetailCardComponent} from '../../../shared/component/detail/detail-card/detail-card.component';
import {DetailHeaderComponent} from '../../../shared/component/detail/detail-header/detail-header.component';
import {DetailStateComponent} from '../../../shared/component/detail/detail-state/detail-state.component';
import {formatDuration, formatTimeCapShort, localizedText, wodTypeLabel} from '../wod.utils';

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
  private readonly unitService = inject(UnitService);

  // --- STATE (SIGNALS) ---
  wod = signal<WodResponse | null>(null);
  isLoading = signal<boolean>(true);

  // --- COMPUTED STATE ---
  activeLang = computed(() => this.translate.currentLang() ?? this.translate.fallbackLang() ?? 'en');

  description = computed(() => localizedText(this.activeLang(), this.wod()?.descriptionEn, this.wod()?.descriptionFr));
  notes = computed(() => localizedText(this.activeLang(), this.wod()?.notesEn, this.wod()?.notesFr));
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

  /** "Girls / For Time". */
  typeLabel(wod: WodResponse): string {
    return wodTypeLabel(key => this.translate.instant(key), wod);
  }

  /** "Tc : 15'" when the WOD has a time cap. */
  timeCapLabel(wod: WodResponse): string {
    const cap = formatTimeCapShort(wod.timeCapSeconds);
    return cap ? `${this.translate.instant('WOD.FIELDS.TIME_CAP_SHORT')} : ${cap}` : '';
  }

  /**
   * Quantity written before the movement on the whiteboard: its reps (omitted when they repeat the
   * WOD's rep scheme, as in "21-15-9 / Thruster / Pull-up"), calories or distance; the duration only
   * when nothing else is given.
   */
  quantity(wm: WodMovementResponse, wod: WodResponse): string {
    const reps = wm.repsScheme && wm.repsScheme !== wod.repScheme ? wm.repsScheme : '';
    const unit = (code?: string) => code ? this.translate.instant('WOD.UNITS.' + code) : '';
    const parts = [
      reps,
      wm.calories ? `${wm.calories} cal` : '',
      wm.distance ? `${wm.distance} ${unit(wm.distanceUnit)}`.trim() : ''
    ].filter(Boolean);
    if (parts.length === 0 && wm.durationSeconds) parts.push(formatDuration(wm.durationSeconds));
    return parts.join(' ');
  }

  /** Equipment and techniques of the movement in the WOD, e.g. "Rings, Strict". */
  variants(wm: WodMovementResponse): string {
    return [
      ...(wm.equipment ?? []).map(eq => this.translate.instant('MOVEMENT.EQUIPMENT.' + eq)),
      ...(wm.techniques ?? []).map(tech => this.translate.instant('MOVEMENT.TECHNIQUES.' + tech))
    ].join(', ');
  }

  /** Men's / women's load in the user's unit: "43/30 kg". */
  load(wm: WodMovementResponse): string {
    return this.unitService.formatLoad(wm.weight, wm.weightFemale, wm.weightUnit);
  }

  movementNotes(wm: WodMovementResponse): string {
    return localizedText(this.activeLang(), wm.notesEn, wm.notesFr);
  }

  scaling(wm: WodMovementResponse): string {
    return localizedText(this.activeLang(), wm.scalingOptionsEn, wm.scalingOptionsFr);
  }
}
