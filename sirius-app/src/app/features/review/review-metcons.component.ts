import {ChangeDetectionStrategy, Component, computed, input} from '@angular/core';
import {TranslatePipe} from '@ngx-translate/core';
import {TIME_DOMAINS, TimeDomain} from '../../api/aldebaran/models/balance.model';
import {ReviewSectionComponent} from './review-section.component';

/** "Metcons" (D86): minutes per time domain over the window. */
@Component({
  selector: 'app-review-metcons',
  standalone: true,
  imports: [TranslatePipe, ReviewSectionComponent],
  templateUrl: './review-metcons.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ReviewMetconsComponent {
  // --- STATE (SIGNALS & VARIABLES) ---
  readonly minutes = input.required<Record<TimeDomain, number>>();
  /** Text when no minute is counted (the coach's balance explains where minutes come from). */
  readonly emptyKey = input('REVIEW.METCONS.EMPTY');

  // --- COMPUTED STATE ---
  readonly rows = computed(() => {
    const minutes = this.minutes();
    const max = Math.max(0, ...TIME_DOMAINS.map(d => minutes[d] ?? 0));
    return TIME_DOMAINS.map(domain => ({
      domain, minutes: minutes[domain] ?? 0, width: max > 0 ? (minutes[domain] ?? 0) / max * 100 : 0
    }));
  });
  readonly isEmpty = computed(() => this.rows().every(r => r.minutes <= 0));
}
