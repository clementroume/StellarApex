import {ChangeDetectionStrategy, Component, computed, inject} from '@angular/core';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {UnitService} from '../../../../core/services/unit.service';
import {WodScoresStore} from '../wod-scores.store';
import {formatScore} from '../score.utils';

/** Summary of the user's scores on the WOD: personal record, its rank and the number of attempts. */
@Component({
  selector: 'app-score-best',
  standalone: true,
  imports: [TranslatePipe],
  templateUrl: './score-best.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ScoreBestComponent {
  // --- INJECTIONS ---
  readonly store = inject(WodScoresStore);
  private readonly unitService = inject(UnitService);
  private readonly translate = inject(TranslateService);

  // --- COMPUTED STATE ---
  readonly bestValue = computed(() => {
    const best = this.store.best();
    const wod = this.store.wod();
    // Read the unit system so the value follows a metric/imperial switch
    this.unitService.unitSystem();
    this.translate.currentLang();
    return best && wod ? formatScore(best, wod, this.unitService, (k, p) => this.translate.instant(k, p)) : '';
  });
}
