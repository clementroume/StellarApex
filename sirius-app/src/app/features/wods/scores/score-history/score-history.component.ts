import {ChangeDetectionStrategy, Component, effect, inject, signal} from '@angular/core';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {NgIconComponent, provideIcons} from '@ng-icons/core';
import {ScoreResponse} from '../../../../api/aldebaran/models/score.model';
import {UnitService} from '../../../../core/services/unit.service';
import {UserService} from '../../../../api/antares/services/user.service';
import {WodScoresStore} from '../wod-scores.store';
import {formatScore, formatScoreLine, levelKey} from '../score.utils';
import {formatDuration} from '../../wod.utils';

/**
 * The current user's scores on the WOD, newest first, with edit/delete when Aldebaran allows it (a
 * verified competition score is locked). Shows who verified a score, or logged it for the user.
 */
import {HlmBadge} from '@spartan-ng/helm/badge';
import {HlmButton} from '@spartan-ng/helm/button';
import {heroCheckBadge, heroClock, heroPencilSquare, heroTrash, heroTrophy, heroUsers} from '@ng-icons/heroicons/outline';
@Component({
  viewProviders: [provideIcons({heroCheckBadge, heroClock, heroPencilSquare, heroTrash, heroTrophy, heroUsers})],
  selector: 'app-score-history',
  standalone: true,
  imports: [HlmButton, HlmBadge, TranslatePipe, NgIconComponent],
  templateUrl: './score-history.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ScoreHistoryComponent {
  // --- INJECTIONS ---
  readonly store = inject(WodScoresStore);
  private readonly unitService = inject(UnitService);
  private readonly translate = inject(TranslateService);
  private readonly userService = inject(UserService);

  // --- STATE (SIGNALS & VARIABLES) ---
  /** Names of the staff who logged or verified the scores (gym mates, visible through Antares). */
  readonly names = signal<Record<number, string>>({});
  protected readonly levelKey = levelKey;

  // --- LIFECYCLE ---
  constructor() {
    effect(() => {
      const scores = this.store.scores();
      const ids = scores.flatMap(s => [s.verifiedBy, s.loggedBy !== s.userId ? s.loggedBy : null]);
      const missing = [...new Set(ids)].filter((id): id is number => id != null && !(id in this.names()));
      if (missing.length > 0) this.loadNames(missing);
    });
  }

  // --- DATA FETCHING ---
  private loadNames(ids: number[]): void {
    this.userService.getDisplayNames(ids).subscribe({
      next: (names) => this.names.update(known => ({...known, ...Object.fromEntries(names.map(n => [n.id, n.displayName]))})),
      error: () => undefined
    });
  }

  // --- HELPERS ---
  /** Display name of a staff member, or a neutral label when Antares does not reveal it. */
  staffName(userId: number | null | undefined): string {
    return (userId != null ? this.names()[userId] : null) ?? this.translate.instant('SCORE.STAFF');
  }

  loggedByStaff(score: ScoreResponse): boolean {
    return score.loggedBy != null && score.loggedBy !== score.userId;
  }

  value(score: ScoreResponse): string {
    const wod = this.store.wod();
    return wod ? formatScore(score, wod, this.unitService, (k, p) => this.translate.instant(k, p)) : '';
  }

  /**
   * Sets of a strength score by line: "Back Squat: 5 x 100 kg · (3 x 120 kg)", a missed set in brackets; without a
   * name when the WOD has a single line.
   */
  setsLines(score: ScoreResponse): string[] {
    const lines = this.store.wod()?.movements ?? [];
    const groups = new Map<number | null, string[]>();
    for (const set of score.sets ?? []) {
      const load = set.weight != null ? ` x ${this.unitService.formatScoreLoad(set.weight, score.weightUnit)}` : '';
      const text = set.completed ? `${set.reps}${load}` : `(${set.reps}${load})`;
      const key = set.wodMovementId ?? null;
      groups.set(key, [...(groups.get(key) ?? []), text]);
    }
    return [...groups.entries()].map(([lineId, sets]) => {
      const line = lineId != null && groups.size > 1 ? lines.find(l => l.id === lineId) : null;
      const name = line ? (line.display?.usualNameEn || line.movement.name) : null;
      return name ? `${name} : ${sets.join(' · ')}` : sets.join(' · ');
    });
  }

  /** Lines done otherwise than prescribed: "Ring Row · 21-15-9". */
  changedLines(score: ScoreResponse): string[] {
    return (score.lines ?? []).map(line => formatScoreLine(line, score.weightUnit, this.unitService));
  }

  duration(seconds: number): string {
    return formatDuration(seconds);
  }

  /** "26 Oct 2026" / "26 oct. 2026", in the language shown. */
  date(score: ScoreResponse): string {
    const lang = this.translate.currentLang() ?? this.translate.fallbackLang() ?? 'en';
    return new Date(`${score.date}T00:00:00`).toLocaleDateString(lang, {day: 'numeric', month: 'short', year: 'numeric'});
  }
}
