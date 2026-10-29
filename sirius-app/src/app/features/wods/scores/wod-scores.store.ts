import {computed, inject, Injectable, signal} from '@angular/core';
import {HttpErrorResponse} from '@angular/common/http';
import {TranslateService} from '@ngx-translate/core';
import {Subject} from 'rxjs';
import {ScoreService} from '../../../api/aldebaran/services/score.service';
import {ScoreComparisonResponse, ScoreRequest, ScoreResponse} from '../../../api/aldebaran/models/score.model';
import {WodResponse} from '../../../api/aldebaran/models/wod.model';
import {NotificationService} from '../../../core/services/notification.service';
import {ProblemDetail} from '../../../core/models/problem-detail.model';

/** The current user's scores on the WOD shown, history size kept on one page. */
const HISTORY_SIZE = 50;

/**
 * State of the current user's scores on one WOD (best score, rank, history, log/edit form), shared
 * by the score components of the WOD detail. Provided by `WodDetailComponent`, so one per screen.
 * Rights come from Aldebaran (`ScoreResponse.capabilities`), never recomputed here.
 */
@Injectable()
export class WodScoresStore {
  // --- INJECTIONS ---
  private readonly scoreService = inject(ScoreService);
  private readonly notificationService = inject(NotificationService);
  private readonly translate = inject(TranslateService);

  // --- STATE (SIGNALS & VARIABLES) ---
  readonly wod = signal<WodResponse | null>(null);
  readonly scores = signal<ScoreResponse[]>([]);
  readonly isLoading = signal<boolean>(false);
  readonly isSaving = signal<boolean>(false);
  readonly comparison = signal<ScoreComparisonResponse | null>(null);
  /** Score being edited, 'new' while logging one, null when the form is closed. */
  readonly editing = signal<ScoreResponse | 'new' | null>(null);
  /**
   * Journal of the session the WOD is shown in (a session block), or null on the WOD detail. New
   * scores join it; Aldebaran takes the journal's day and gym.
   */
  readonly sessionLogId = signal<number | null>(null);
  /** Emits after a score is logged, updated or deleted (the WOD may become locked or unlocked). */
  readonly changed$ = new Subject<void>();

  // --- COMPUTED STATE ---
  /** Personal record, flagged by Aldebaran (scaling level first, then performance). */
  readonly best = computed(() => this.scores().find(s => s.personalRecord) ?? null);

  // --- DATA FETCHING ---
  load(wod: WodResponse): void {
    this.wod.set(wod);
    this.isLoading.set(true);
    this.scoreService.getMyScores(wod.id, 0, HISTORY_SIZE).subscribe({
      next: (slice) => {
        this.scores.set(slice.content);
        this.isLoading.set(false);
        this.loadComparison();
      },
      error: () => {
        this.scores.set([]);
        this.isLoading.set(false);
      }
    });
  }

  private loadComparison(): void {
    const best = this.best();
    this.comparison.set(null);
    if (!best || this.wod()?.scoreType === 'NONE') return;
    this.scoreService.compareScore(best.id).subscribe({
      next: (comparison) => this.comparison.set(comparison),
      error: () => this.comparison.set(null)
    });
  }

  // --- ACTIONS & EVENTS ---
  openNew(): void {
    this.editing.set('new');
  }

  openEdit(score: ScoreResponse): void {
    this.editing.set(score);
  }

  close(): void {
    this.editing.set(null);
  }

  save(request: ScoreRequest): void {
    const editing = this.editing();
    const sessionLogId = this.sessionLogId();
    const call = editing && editing !== 'new'
      ? this.scoreService.updateScore(editing.id, request)
      : this.scoreService.logScore(sessionLogId != null ? {...request, sessionLogId} : request);

    this.isSaving.set(true);
    call.subscribe({
      next: () => {
        this.isSaving.set(false);
        this.notificationService.showSuccess(this.translate.instant('SCORE.MESSAGES.SAVED'));
        this.afterChange();
      },
      error: (err: HttpErrorResponse) => {
        this.isSaving.set(false);
        this.showError(err, 'SCORE.MESSAGES.SAVE_ERROR');
      }
    });
  }

  /** Verifies a competition score (judging) or withdraws its verification; Aldebaran checks the right. */
  setVerified(score: ScoreResponse, verified: boolean): void {
    const call = verified ? this.scoreService.verifyScore(score.id) : this.scoreService.unverifyScore(score.id);
    call.subscribe({
      next: () => {
        const key = verified ? 'SCORE.MESSAGES.VERIFIED' : 'SCORE.MESSAGES.UNVERIFIED';
        this.notificationService.showSuccess(this.translate.instant(key));
        this.afterChange();
      },
      error: (err: HttpErrorResponse) => this.showError(err, 'SCORE.MESSAGES.SAVE_ERROR')
    });
  }

  delete(score: ScoreResponse): void {
    if (!confirm(this.translate.instant('SCORE.MESSAGES.CONFIRM_DELETE'))) return;
    this.scoreService.deleteScore(score.id).subscribe({
      next: () => {
        this.notificationService.showSuccess(this.translate.instant('GLOBAL.DELETE_SUCCESS'));
        this.afterChange();
      },
      error: (err: HttpErrorResponse) => this.showError(err, 'GLOBAL.DELETE_ERROR')
    });
  }

  // --- HELPERS ---
  private afterChange(): void {
    this.close();
    const wod = this.wod();
    if (wod) this.load(wod);
    this.changed$.next();
  }

  private showError(err: HttpErrorResponse, fallbackKey: string): void {
    const problem: ProblemDetail | undefined = err.error;
    if (err.status === 403) {
      this.notificationService.showError(this.translate.instant('SCORE.MESSAGES.FORBIDDEN'));
    } else {
      // Bean Validation errors carry their localized messages in `errors`, the detail is generic
      const fieldError: string | undefined = problem?.['errors']?.[0]?.message;
      this.notificationService.showError(fieldError || problem?.detail || this.translate.instant(fallbackKey));
    }
  }
}
