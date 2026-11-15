import {ChangeDetectionStrategy, Component, computed, input} from '@angular/core';
import {TranslatePipe} from '@ngx-translate/core';
import {Modality, MODALITIES} from '../../api/aldebaran/models/balance.model';
import {ReviewSectionComponent} from './review-section.component';

/** Colour of each modality (D88): gymnastics primary, weightlifting secondary, monostructural accent. */
const COLOURS: Record<Modality, string> = {GYMNASTICS: 'bg-primary', WEIGHTLIFTING: 'bg-secondary', MONOSTRUCTURAL: 'bg-accent'};

/** "Modalités" (D86, D127): the share of the sets in each modality of the CrossFit Level 1, no numbers. */
@Component({
  selector: 'app-review-modalities',
  standalone: true,
  imports: [TranslatePipe, ReviewSectionComponent],
  templateUrl: './review-modalities.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ReviewModalitiesComponent {
  // --- STATE (SIGNALS & VARIABLES) ---
  readonly modalities = input.required<Record<Modality, number>>();

  // --- COMPUTED STATE ---
  readonly parts = computed(() => {
    const m = this.modalities();
    const total = MODALITIES.reduce((sum, k) => sum + (m[k] ?? 0), 0);
    return MODALITIES.map(k => ({key: k, colour: COLOURS[k], width: total > 0 ? (m[k] ?? 0) / total * 100 : 0}));
  });
  readonly isEmpty = computed(() => this.parts().every(p => p.width <= 0));
}
