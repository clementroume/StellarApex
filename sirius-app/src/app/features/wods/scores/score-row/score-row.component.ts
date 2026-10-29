import {ChangeDetectionStrategy, Component, computed, inject, input} from '@angular/core';
import {RouterLink} from '@angular/router';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {HlmBadge} from '@spartan-ng/helm/badge';
import {ScoreResponse} from '../../../../api/aldebaran/models/score.model';
import {UnitService} from '../../../../core/services/unit.service';
import {formatScore} from '../score.utils';

/** One of the user's scores as a link to its WOD: title, date, PR badge and formatted score. */
@Component({
  selector: 'app-score-row',
  standalone: true,
  imports: [RouterLink, TranslatePipe, HlmBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a [routerLink]="['/wods', score().wodSummary.id]"
       class="flex items-center justify-between gap-3 py-3 px-2 -mx-2 rounded-md hover:bg-muted/50">
      <span class="min-w-0">
        <span class="block font-medium truncate">{{ score().wodSummary.title }}</span>
        <span class="block text-xs text-muted-foreground">
          {{ date() }}
          · {{ 'SCORE.SCALING.' + score().scaling | translate }}
          @if (score().sets?.length) {
            · {{ 'SCORE.SETS.SUMMARY' | translate: {count: score().sets?.length} }}
          }
        </span>
      </span>
      <span class="flex items-center gap-2 shrink-0">
        @if (score().personalRecord) {
          <span hlmBadge class="bg-secondary/20 text-secondary">{{ 'TODAY.PR' | translate }}</span>
        }
        <span class="font-semibold tabular-nums">{{ display() }}</span>
      </span>
    </a>
  `
})
export class ScoreRowComponent {
  // --- INJECTIONS ---
  private readonly unitService = inject(UnitService);
  private readonly translate = inject(TranslateService);

  // --- STATE (SIGNALS & VARIABLES) ---
  readonly score = input.required<ScoreResponse>();

  // --- COMPUTED STATE ---
  /** "26 Oct 2026" / "26 oct. 2026", in the language shown. */
  protected readonly date = computed(() => {
    const lang = this.translate.currentLang() ?? this.translate.fallbackLang() ?? 'en';
    return new Date(`${this.score().date}T00:00:00`).toLocaleDateString(lang, {day: 'numeric', month: 'short', year: 'numeric'});
  });
  protected readonly display = computed(() =>
    formatScore(this.score(), this.score().wodSummary, this.unitService, (k, p) => this.translate.instant(k, p)));
}
