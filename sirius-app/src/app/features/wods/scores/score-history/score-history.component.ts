import {ChangeDetectionStrategy, Component, inject} from '@angular/core';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {NgIconComponent} from '@ng-icons/core';
import {ScoreResponse} from '../../../../api/aldebaran/models/score.model';
import {UnitService} from '../../../../core/services/unit.service';
import {WodScoresStore} from '../wod-scores.store';
import {formatScore} from '../score.utils';
import {formatDuration} from '../../wod.utils';

/** The current user's scores on the WOD, newest first, with edit/delete when Aldebaran allows it. */
@Component({
  selector: 'app-score-history',
  standalone: true,
  imports: [TranslatePipe, NgIconComponent],
  templateUrl: './score-history.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ScoreHistoryComponent {
  // --- INJECTIONS ---
  readonly store = inject(WodScoresStore);
  private readonly unitService = inject(UnitService);
  private readonly translate = inject(TranslateService);

  // --- HELPERS ---
  value(score: ScoreResponse): string {
    const wod = this.store.wod();
    return wod ? formatScore(score, wod, this.unitService, (k, p) => this.translate.instant(k, p)) : '';
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
