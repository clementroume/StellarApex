import {ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {RouterLink} from '@angular/router';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {NgIcon} from '@ng-icons/core';
import {HlmBadge} from '@spartan-ng/helm/badge';
import {HlmButton} from '@spartan-ng/helm/button';
import {SessionBlockResponse, SessionLogResponse} from '../../../api/aldebaran/models/session.model';
import {ScoreResponse} from '../../../api/aldebaran/models/score.model';
import {WodResponse} from '../../../api/aldebaran/models/wod.model';
import {WodService} from '../../../api/aldebaran/services/wod.service';
import {ScoreService} from '../../../api/aldebaran/services/score.service';
import {UserService} from '../../../api/antares/services/user.service';
import {UnitService} from '../../../core/services/unit.service';
import {WodScoresStore} from '../../wods/scores/wod-scores.store';
import {ScoreHistoryComponent} from '../../wods/scores/score-history/score-history.component';
import {ScoreFormComponent} from '../../wods/scores/score-form/score-form.component';
import {ScoreLeaderboardComponent} from '../../wods/scores/score-leaderboard/score-leaderboard.component';
import {formatScore} from '../../wods/scores/score.utils';
import {WodWhiteboardComponent} from '../../wods/wod-whiteboard/wod-whiteboard.component';
import {localizedText} from '../../wods/wod.utils';
import {blockTheme, isTextKind} from '../session.utils';

/**
 * One block of a session: kind, label and theme, then its text (warm-up, cool down) or its WOD on the
 * whiteboard. A scored block shows the user's scores and the score form (`WodScoresStore` per block),
 * linked to the user's journal of the session; the judge of a competition block gets the scores
 * submitted for verification. Rights come from Aldebaran (WOD and score capabilities).
 */
@Component({
  selector: 'app-session-block',
  standalone: true,
  imports: [RouterLink, TranslatePipe, NgIcon, HlmBadge, HlmButton, ScoreHistoryComponent, ScoreFormComponent,
    ScoreLeaderboardComponent,
    WodWhiteboardComponent],
  providers: [WodScoresStore],
  templateUrl: './session-block.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SessionBlockComponent {
  // --- INJECTIONS ---
  private readonly translate = inject(TranslateService);
  private readonly wodService = inject(WodService);
  private readonly scoreService = inject(ScoreService);
  private readonly userService = inject(UserService);
  private readonly unitService = inject(UnitService);
  protected readonly store = inject(WodScoresStore);

  // --- STATE (SIGNALS & VARIABLES) ---
  readonly block = input.required<SessionBlockResponse>();
  /** Session of the block: history and leaderboard keep that session's scores. */
  readonly sessionId = input<number | null>(null);
  /** The user's journal of the session, or null before "Séance faite". */
  readonly log = input<SessionLogResponse | null>(null);
  /** Emits after a score of the block changed (the journal may have been created). */
  readonly scoresChanged = output<void>();
  readonly doneChange = output<boolean>();
  /** The block's WOD with the user's capabilities (judging), loaded apart from the session. */
  protected readonly wod = signal<WodResponse | null>(null);
  protected readonly queue = signal<ScoreResponse[]>([]);
  /** Session leaderboard of the block, folded by default. */
  protected readonly showLeaderboard = signal(false);
  private readonly names = signal<Record<number, string>>({});

  // --- COMPUTED STATE ---
  private readonly lang = computed(() => this.translate.currentLang() ?? this.translate.fallbackLang() ?? 'en');
  protected readonly isText = computed(() => isTextKind(this.block().kind));
  protected readonly theme = computed(() => blockTheme(this.lang(), this.block()));
  protected readonly text = computed(() => localizedText(this.lang(), this.block().textEn, this.block().textFr));
  protected readonly done = computed(() => {
    const entry = this.log()?.blocks.find(b => b.blockId === this.block().id);
    return entry?.done ?? false;
  });
  /**
   * Scores join the journal; a borrowed WOD needs the journal first (else the score would not belong
   * to the session), a session WOD creates it with the score.
   */
  protected readonly canLogScore = computed(() => !!this.block().wod && (!!this.log() || !this.block().borrowed));
  protected readonly canJudge = computed(() => !!this.wod()?.capabilities?.canJudge);

  // --- LIFECYCLE ---
  constructor() {
    effect(() => {
      const wod = this.block().wod;
      untracked(() => wod ? this.loadWod(wod.id) : this.wod.set(null));
    });
    effect(() => this.store.sessionLogId.set(this.log()?.id ?? null));
    this.store.changed$.pipe(takeUntilDestroyed()).subscribe(() => {
      this.scoresChanged.emit();
      if (this.canJudge()) this.loadQueue();
    });
  }

  // --- DATA FETCHING ---
  private loadWod(id: number): void {
    this.wodService.getWod(id).subscribe({
      next: wod => {
        this.wod.set(wod);
        this.store.sessionId.set(this.sessionId());
        this.store.load(wod);
        if (wod.capabilities?.canJudge) this.loadQueue();
      },
      error: () => this.wod.set(null)
    });
  }

  private loadQueue(): void {
    const wod = this.wod();
    if (!wod) return;
    this.scoreService.getVerificationQueue(wod.id).subscribe({
      next: scores => {
        this.queue.set(scores);
        const ids = [...new Set(scores.map(s => s.userId))].filter(id => !(id in this.names()));
        if (ids.length > 0) {
          this.userService.getDisplayNames(ids).subscribe(list =>
            this.names.update(n => ({...n, ...Object.fromEntries(list.map(d => [d.id, d.displayName]))})));
        }
      },
      error: () => this.queue.set([])
    });
  }

  // --- ACTIONS & EVENTS ---
  protected verify(score: ScoreResponse): void {
    this.store.setVerified(score, true);
  }

  protected toggleDone(event: Event): void {
    this.doneChange.emit((event.target as HTMLInputElement).checked);
  }

  // --- HELPERS ---
  protected athleteName(score: ScoreResponse): string {
    return this.names()[score.userId] ?? `#${score.userId}`;
  }

  protected scoreValue(score: ScoreResponse): string {
    const wod = this.wod();
    return wod ? formatScore(score, wod, this.unitService, (k, p) => this.translate.instant(k, p)) : '';
  }
}
