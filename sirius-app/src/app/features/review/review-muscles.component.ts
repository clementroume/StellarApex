import {ChangeDetectionStrategy, Component, computed, input, signal} from '@angular/core';
import {RouterLink} from '@angular/router';
import {TranslatePipe} from '@ngx-translate/core';
import {HlmAccordionImports} from '@spartan-ng/helm/accordion';
import {MuscleBalance} from '../../api/aldebaran/models/balance.model';
import {BodyMapComponent} from '../../shared/component/body-map/body-map.component';
import {ReviewSectionComponent} from './review-section.component';
import {level, MuscleTab, muscleName, REGIONS, setsOf, sumBy, zoneSets} from './review.utils';

/** A bar of a region or of one of its muscles. */
interface Bar {
  key: string;
  label: string;
  /** Width, in % of the largest of the list. */
  width: number;
  level: number;
  muscleId?: number;
}

/**
 * "Muscles sollicités" (D88 bis, D123, D129): two tabs, moving and holding, each on its own scale; the
 * drawing shaded per zone, primary colour for moving and the third colour (accent) for holding (a picture, not a control, hidden from assistive technologies), the 7 region
 * bars sorted by value, without numbers. A region opens its muscles right under its bar (accordion).
 */
@Component({
  selector: 'app-review-muscles',
  standalone: true,
  imports: [RouterLink, TranslatePipe, HlmAccordionImports, BodyMapComponent, ReviewSectionComponent],
  templateUrl: './review-muscles.component.html',
  styleUrl: './review-muscles.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {'[class.holding]': "tab() === 'holding'"}
})
export class ReviewMusclesComponent {
  // --- STATE (SIGNALS & VARIABLES) ---
  readonly muscles = input.required<MuscleBalance[]>();
  readonly lang = input<string>('fr');
  readonly tab = signal<MuscleTab>('moving');
  readonly tabs: MuscleTab[] = ['moving', 'holding'];
  /** Bar colour of the tab, as on the drawing. */
  readonly barColour = computed(() => this.tab() === 'holding' ? 'bg-accent' : 'bg-primary');

  // --- COMPUTED STATE ---
  private readonly zones = computed(() => zoneSets(this.muscles(), this.tab()));
  /** Shade of each zone, the most worked of the tab = 5. */
  readonly heat = computed(() => {
    const zones = this.zones();
    const max = Math.max(0, ...Object.values(zones));
    return Object.fromEntries(Object.entries(zones).map(([zone, sets]) => [zone, level(sets, max)]));
  });
  readonly isEmpty = computed(() => this.muscles().every(m => setsOf(m, this.tab()) <= 0));
  readonly regionBars = computed<Bar[]>(() => {
    const sums = sumBy(this.muscles(), this.tab(), m => m.region);
    const max = Math.max(0, ...Object.values(sums));
    return REGIONS
      .map(region => ({region, sets: sums[region] ?? 0}))
      .sort((a, b) => b.sets - a.sets)
      .map(({region, sets}) => ({
        key: region, label: `MUSCLE.REGIONS.${region}`,
        width: max > 0 ? sets / max * 100 : 0, level: level(sets, max)
      }));
  });
  /** Muscles of each region worked in the tab, the most worked first, on the region's own scale. */
  readonly musclesByRegion = computed<Record<string, Bar[]>>(() => {
    const tab = this.tab();
    const byRegion: Record<string, Bar[]> = {};
    for (const region of REGIONS) {
      const rows = this.muscles()
        .filter(m => m.region === region && setsOf(m, tab) > 0)
        .sort((a, b) => setsOf(b, tab) - setsOf(a, tab));
      const max = rows.length ? setsOf(rows[0], tab) : 0;
      byRegion[region] = rows.map(m => ({
        key: String(m.muscleId), muscleId: m.muscleId, label: muscleName(m, this.lang()),
        width: max > 0 ? setsOf(m, tab) / max * 100 : 0, level: level(setsOf(m, tab), max)
      }));
    }
    return byRegion;
  });

  // --- ACTIONS & EVENTS ---
  pickTab(tab: MuscleTab): void {
    this.tab.set(tab);
  }
}
