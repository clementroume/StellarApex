import {ChangeDetectionStrategy, Component, computed, effect, inject, input, signal} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {Router, RouterModule} from '@angular/router';
import {Location} from '@angular/common';
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
import {formatDuration, formatTimeCapShort, localizedText} from '../wod.utils';
import {WodScoresStore} from '../scores/wod-scores.store';
import {ScoreBestComponent} from '../scores/score-best/score-best.component';
import {ScoreHistoryComponent} from '../scores/score-history/score-history.component';
import {ScoreFormComponent} from '../scores/score-form/score-form.component';
import {ScoreLeaderboardComponent} from '../scores/score-leaderboard/score-leaderboard.component';

export type WodVisibility = 'PUBLIC' | 'GYM' | 'PRIVATE';

// Rep schemes that only restate the format ("AMRAP 20 min", "For time", "Tabata x 4")
const FORMAT_SCHEME = /^(amrap|emom|tabata|for time)\b/i;
// Rep schemes that describe the structure rather than list the movement quantities
const STRUCTURE_SCHEME = /round|tier|window|rm\b| x /i;

@Component({
  selector: 'app-wod-detail',
  standalone: true,
  imports: [TranslatePipe, RouterModule, NgIconComponent, DetailAdminActionsComponent, DetailCardComponent, DetailHeaderComponent, DetailStateComponent, ScoreBestComponent, ScoreHistoryComponent, ScoreFormComponent, ScoreLeaderboardComponent],
  providers: [WodScoresStore],
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
  readonly scoresStore = inject(WodScoresStore);

  // --- STATE (SIGNALS) ---
  wod = signal<WodResponse | null>(null);
  isLoading = signal<boolean>(true);

  // --- COMPUTED STATE ---
  activeLang = computed(() => this.translate.currentLang() ?? this.translate.fallbackLang() ?? 'en');

  description = computed(() => localizedText(this.activeLang(), this.wod()?.descriptionEn, this.wod()?.descriptionFr));
  notes = computed(() => localizedText(this.activeLang(), this.wod()?.notesEn, this.wod()?.notesFr));
  tiebreak = computed(() => localizedText(this.activeLang(), this.wod()?.tiebreakEn, this.wod()?.tiebreakFr));
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
    // The first score locks the WOD, deleting the last one unlocks it: refresh its capabilities
    this.scoresStore.changed$.pipe(takeUntilDestroyed()).subscribe(() => this.refreshWod());
  }

  // --- DATA FETCHING ---
  private loadWod(id: number): void {
    this.isLoading.set(true);

    this.wodService.getWod(id).subscribe({
      next: (w) => {
        this.wod.set(w);
        this.isLoading.set(false);
        this.scoresStore.load(w);
      },
      error: () => {
        this.notificationService.showError(this.translate.instant('WOD.MESSAGES.NOT_FOUND'));
        this.isLoading.set(false);
        this.location.back();
      }
    });
  }

  private refreshWod(): void {
    const current = this.wod();
    if (!current) return;
    this.wodService.getWod(current.id).subscribe({next: (w) => this.wod.set(w)});
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
  /**
   * First line of the whiteboard: the format with its duration when the duration is the workout itself
   * ("AMRAP 20 minutes", "EMOM 10 minutes", "EMOM 5 x 2:00"), else the format alone ("For Time").
   */
  formatLine(wod: WodResponse): string {
    const format = this.translate.instant('WOD.WOD_TYPES.' + wod.wodType);
    if (wod.emomInterval && wod.emomRounds) {
      return wod.emomInterval === 60
        ? `${format} ${this.minutes(wod.emomRounds * 60)}`
        : `${format} ${wod.emomRounds} x ${formatDuration(wod.emomInterval)}`;
    }
    if (wod.wodType === 'AMRAP' && wod.timeCapSeconds) return `${format} ${this.minutes(wod.timeCapSeconds)}`;
    const scheme = wod.repScheme?.trim() ?? '';
    // "EMOM 30 min", "Tabata x 4": the rep scheme already is the format line
    return FORMAT_SCHEME.test(scheme) && !/^for time$/i.test(scheme) ? scheme : format;
  }

  /**
   * Rep scheme line, only when it adds something: the structure ("5 rounds", "3 x (15 / 30 / 15)") or
   * reps shared by the movements ("21-15-9"). A restated format or a list of the movement quantities
   * ("1 mile / 100 / 200 / 300 / 1 mile") is left out, the movement lines already say it.
   */
  repSchemeLine(wod: WodResponse): string {
    const scheme = wod.repScheme?.trim() ?? '';
    if (!scheme || FORMAT_SCHEME.test(scheme)) return '';
    const shared = wod.movements.some(wm => wm.repsScheme === wod.repScheme);
    return shared || STRUCTURE_SCHEME.test(scheme) ? scheme : '';
  }

  /** "Time cap 15'", unless the time cap is already the duration of the format line (AMRAP). */
  timeCapLabel(wod: WodResponse): string {
    const cap = formatTimeCapShort(wod.timeCapSeconds);
    if (!cap || wod.wodType === 'AMRAP') return '';
    return `${this.translate.instant('WOD.FIELDS.TIME_CAP')} ${cap}`;
  }

  /**
   * Quantity written before the movement on the whiteboard: its reps (omitted when they repeat the
   * WOD's rep scheme, as in "21-15-9 / Thruster / Pull-up"), men's/women's calories or distance; the
   * duration only when nothing else is given.
   */
  quantity(wm: WodMovementResponse, wod: WodResponse): string {
    // "max" is written "Max" on the board
    const reps = wm.repsScheme && wm.repsScheme !== wod.repScheme ? wm.repsScheme.replace(/^max\b/, 'Max') : '';
    const calories = [wm.calories, wm.caloriesFemale].filter(v => v != null).join('/');
    const parts = [
      reps,
      calories ? `${calories} cal` : '',
      this.unitService.formatDistance(wm.distance, null, wm.distanceUnit)
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

  /** Men's / women's load and height in the user's units, written after the movement: "61/43 kg", "60/50 cm". */
  measures(wm: WodMovementResponse): string[] {
    return [
      this.unitService.formatLoad(wm.weight, wm.weightFemale, wm.weightUnit),
      this.unitService.formatDistance(wm.height, wm.heightFemale, wm.heightUnit)
    ].filter(Boolean);
  }

  movementNotes(wm: WodMovementResponse): string {
    return localizedText(this.activeLang(), wm.notesEn, wm.notesFr);
  }

  scaling(wm: WodMovementResponse): string {
    return localizedText(this.activeLang(), wm.scalingOptionsEn, wm.scalingOptionsFr);
  }

  /** "20 minutes", or "7'05" for a duration that is not a whole number of minutes. */
  private minutes(seconds: number): string {
    return seconds % 60 === 0
      ? this.translate.instant('WOD.FIELDS.DURATION_MINUTES', {count: seconds / 60})
      : formatTimeCapShort(seconds);
  }
}
