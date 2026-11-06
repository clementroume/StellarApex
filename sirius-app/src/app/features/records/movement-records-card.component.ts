import {ChangeDetectionStrategy, Component, computed, inject, input} from '@angular/core';
import {RouterLink} from '@angular/router';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {HlmBadge} from '@spartan-ng/helm/badge';
import {CanonicalLift, MovementRecords, RecordEntry, VariantRecords} from '../../api/aldebaran/models/record.model';
import {UnitService} from '../../core/services/unit.service';
import {variantLabel} from './record.utils';

/**
 * Records of one movement (D61): the base version first (each N RM with its day, and the estimated 1RM,
 * displayed only), the variants folded under it, and the canonical "N RM" tests to log a new one.
 * Used by "My records" and by the movement page.
 */
@Component({
  selector: 'app-movement-records-card',
  standalone: true,
  imports: [RouterLink, TranslatePipe, HlmBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './movement-records-card.component.html'
})
export class MovementRecordsCardComponent {
  // --- INJECTIONS ---
  protected readonly unitService = inject(UnitService);
  private readonly translate = inject(TranslateService);
  readonly movementId = input.required<number>();
  readonly movementName = input.required<string>();
  readonly records = input<MovementRecords | undefined>();
  readonly lifts = input<CanonicalLift[]>([]);
  readonly showName = input<boolean>(true);
  /** Says so when the base version has no record (off where only the tests are listed). */
  readonly showEmpty = input<boolean>(true);

  // --- COMPUTED STATE ---
  readonly base = computed<VariantRecords | undefined>(() => this.records()?.variants.find(v => v.base));
  readonly variants = computed<VariantRecords[]>(() => this.records()?.variants.filter(v => !v.base) ?? []);

  // --- HELPERS ---
  load(r: RecordEntry): string {
    return this.unitService.formatScoreLoad(r.weightKg, 'KG');
  }

  day(r: RecordEntry): string {
    const lang = this.translate.currentLang() ?? 'en';
    return new Date(`${r.date}T00:00:00`).toLocaleDateString(lang, {day: 'numeric', month: 'short', year: 'numeric'});
  }

  label(v: VariantRecords): string {
    return variantLabel(v, key => this.translate.instant(key));
  }
}
