import {ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {NgIconComponent} from '@ng-icons/core';
import {ScalingLevel, ScoreResponse} from '../../../../api/aldebaran/models/score.model';
import {ScoreService} from '../../../../api/aldebaran/services/score.service';
import {UserService} from '../../../../api/antares/services/user.service';
import {AccessService} from '../../../../core/services/access.service';
import {UnitService} from '../../../../core/services/unit.service';
import {formatDuration} from '../../wod.utils';
import {WodScoresStore} from '../wod-scores.store';
import {formatScore, SCALING_LEVELS} from '../score.utils';

/** Leaderboard rows per page. */
const PAGE_SIZE = 50;

/**
 * Leaderboard of the WOD shown: each athlete's personal record at the chosen level, best first,
 * for every gym or one of the user's gyms. Names come from Antares, which only returns those the
 * user may see (gym mates, athletes who opted in, themselves); the others read "anonymous athlete".
 */
@Component({
  selector: 'app-score-leaderboard',
  standalone: true,
  imports: [TranslatePipe, NgIconComponent],
  templateUrl: './score-leaderboard.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ScoreLeaderboardComponent {
  // --- INJECTIONS ---
  readonly store = inject(WodScoresStore);
  private readonly scoreService = inject(ScoreService);
  private readonly userService = inject(UserService);
  private readonly accessService = inject(AccessService);
  private readonly unitService = inject(UnitService);
  private readonly translate = inject(TranslateService);

  // --- STATE (SIGNALS & VARIABLES) ---
  readonly scalingLevels = SCALING_LEVELS;
  readonly scaling = signal<ScalingLevel>('RX');
  /** Gym to keep on a public or personal WOD, null for every gym. */
  readonly gymId = signal<number | null>(null);
  readonly rows = signal<ScoreResponse[]>([]);
  readonly names = signal<Record<number, string>>({});
  readonly hasMore = signal<boolean>(false);
  readonly isLoading = signal<boolean>(false);
  private page = 0;

  // --- COMPUTED STATE ---
  readonly myId = computed(() => this.accessService.userId());
  /** A gym WOD is only ranked within its gym; a public WOD can be filtered on the user's gyms. */
  readonly gymChoices = computed(() => this.store.wod()?.gymId != null ? [] : this.accessService.memberships());

  // --- LIFECYCLE ---
  constructor() {
    effect(() => {
      const wod = this.store.wod();
      this.scaling();
      this.gymId();
      if (wod) untracked(() => this.load(true));
    });
    // A logged, edited or deleted score may change the ranking
    this.store.changed$.pipe(takeUntilDestroyed()).subscribe(() => this.load(true));
  }

  // --- DATA FETCHING ---
  load(reset: boolean): void {
    const wod = this.store.wod();
    if (!wod) return;
    this.page = reset ? 0 : this.page + 1;
    this.isLoading.set(true);
    this.scoreService.getLeaderboard(wod.id, this.scaling(), this.page, PAGE_SIZE, this.gymId()).subscribe({
      next: (slice) => {
        this.rows.set(reset ? slice.content : [...this.rows(), ...slice.content]);
        this.hasMore.set(!slice.last);
        this.isLoading.set(false);
        this.loadNames(slice.content);
      },
      error: () => {
        if (reset) this.rows.set([]);
        this.hasMore.set(false);
        this.isLoading.set(false);
      }
    });
  }

  private loadNames(scores: ScoreResponse[]): void {
    const known = this.names();
    const missing = [...new Set(scores.map(s => s.userId))].filter(id => !(id in known));
    if (missing.length === 0) return;
    this.userService.getDisplayNames(missing).subscribe({
      next: (names) => this.names.set({...this.names(), ...Object.fromEntries(names.map(n => [n.id, n.displayName]))}),
      error: () => undefined
    });
  }

  // --- ACTIONS & EVENTS ---
  onScalingChange(level: ScalingLevel): void {
    this.scaling.set(level);
  }

  onGymChange(value: string): void {
    this.gymId.set(value ? Number(value) : null);
  }

  // --- HELPERS ---
  name(score: ScoreResponse): string {
    return this.names()[score.userId] ?? this.translate.instant('SCORE.LEADERBOARD.ANONYMOUS');
  }

  value(score: ScoreResponse): string {
    const wod = this.store.wod();
    return wod ? formatScore(score, wod, this.unitService, (k, p) => this.translate.instant(k, p)) : '';
  }

  tiebreak(score: ScoreResponse): string {
    return score.tiebreakSeconds ? formatDuration(score.tiebreakSeconds) : '';
  }
}
