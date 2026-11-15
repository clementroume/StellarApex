import {ChangeDetectionStrategy, Component, computed, inject, input} from '@angular/core';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {LiftBalance} from '../../api/aldebaran/models/balance.model';
import {ReviewSectionComponent} from './review-section.component';
import {joinNames} from './review.utils';

/** A slice of the ring. */
interface Slice {
  label: string;
  /** Share of the ring, in % (the ring's circumference is 100). */
  share: number;
  /** Where it starts, in % from the top. */
  offset: number;
  /** Colour: the three theme colours, then the first two half transparent (0 = the largest share). */
  colour: number;
  /** Drawn length, a little shorter than the share so that slices stay apart. */
  dash: number;
}

/** Most slices; beyond, the smallest are grouped under "Others" (D88). */
const MAX_SLICES = 5;
/** Gap between two slices, in % of the ring. */
const GAP = 1;

/**
 * "Force" (D88, D124): the share of each lift in the strength sets of the window (a base movement, or a
 * complex under its name), and the lifts done close to the record, named without numbers.
 */
@Component({
  selector: 'app-review-force',
  standalone: true,
  imports: [TranslatePipe, ReviewSectionComponent],
  templateUrl: './review-force.component.html',
  styleUrl: './review-force.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ReviewForceComponent {
  // --- INJECTIONS ---
  private readonly translate = inject(TranslateService);

  // --- STATE (SIGNALS & VARIABLES) ---
  readonly lifts = input.required<LiftBalance[]>();

  // --- COMPUTED STATE ---
  readonly slices = computed<Slice[]>(() => {
    const lifts = [...this.lifts()].filter(l => l.sets > 0).sort((a, b) => b.sets - a.sets);
    const total = lifts.reduce((sum, l) => sum + l.sets, 0);
    if (total <= 0) return [];
    const shown = lifts.length > MAX_SLICES ? lifts.slice(0, MAX_SLICES - 1) : lifts;
    const parts = shown.map(l => ({label: l.label, sets: l.sets}));
    if (lifts.length > MAX_SLICES) {
      parts.push({
        label: this.translate.instant('REVIEW.FORCE.OTHERS'),
        sets: lifts.slice(MAX_SLICES - 1).reduce((sum, l) => sum + l.sets, 0)
      });
    }
    const gap = parts.length > 1 ? GAP : 0;
    let offset = 0;
    return parts.map((p, i) => {
      const share = p.sets / total * 100;
      const slice = {label: p.label, share, offset, colour: i, dash: Math.max(share - gap, 0.5)};
      offset += share;
      return slice;
    });
  });
  /** "Près de ton record : Squat et Clean and Jerk." */
  readonly nearRecord = computed(() => {
    const names = this.lifts().filter(l => l.nearRecord).map(l => l.label);
    return names.length
      ? this.translate.instant('REVIEW.FORCE.NEAR', {lifts: joinNames(names, this.translate.instant('REVIEW.FORCE.AND'))})
      : null;
  });
}
