import {ChangeDetectionStrategy, Component, computed, input} from '@angular/core';
import {TranslatePipe} from '@ngx-translate/core';
import {WeekVolume} from '../../api/aldebaran/models/balance.model';
import {shortDay} from '../sessions/session.utils';
import {ReviewSectionComponent} from './review-section.component';
import {level} from './review.utils';

/** "Volume" (D88, D130): equivalent sets of the chosen week (highlighted) and the 7 before, no numbers. */
@Component({
  selector: 'app-review-volume',
  standalone: true,
  imports: [TranslatePipe, ReviewSectionComponent],
  templateUrl: './review-volume.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ReviewVolumeComponent {
  // --- STATE (SIGNALS & VARIABLES) ---
  readonly volume = input.required<WeekVolume[]>();
  readonly lang = input<string>('fr');

  // --- COMPUTED STATE ---
  readonly bars = computed(() => {
    const weeks = this.volume();
    const max = Math.max(0, ...weeks.map(w => w.sets));
    return weeks.map((w, i) => ({
      key: w.weekStart,
      day: shortDay(w.weekStart, this.lang()),
      height: max > 0 && w.sets > 0 ? Math.max(4, w.sets / max * 100) : 4,
      empty: w.sets <= 0,
      chosen: i === weeks.length - 1,
      level: level(w.sets, max)
    }));
  });
}
