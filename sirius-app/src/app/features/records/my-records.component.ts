import {ChangeDetectionStrategy, Component, computed, inject, signal} from '@angular/core';
import {HttpContext} from '@angular/common/http';
import {RouterLink} from '@angular/router';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {HlmBadge} from '@spartan-ng/helm/badge';
import {HlmButton} from '@spartan-ng/helm/button';
import {HlmSpinner} from '@spartan-ng/helm/spinner';
import {forkJoin} from 'rxjs';
import {CanonicalLift, MovementRecords} from '../../api/aldebaran/models/record.model';
import {RecordService} from '../../api/aldebaran/services/record.service';
import {BYPASS_LOADER} from '../../core/interceptors/loading.interceptor';
import {UnitService} from '../../core/services/unit.service';
import {PageComponent} from '../../shared/component/page/page.component';
import {crossfitTotal, familyOf, LIFT_FAMILIES, LiftFamily, LiftSummary, liftSummary} from './record.utils';

/** A tile of the board: a movement with records. */
interface LiftTile {
  movementId: number;
  movementName: string;
  family: LiftFamily;
  summary: LiftSummary;
}

/** A movement with canonical tests and no record yet. */
interface UntestedLift {
  movementId: number;
  movementName: string;
  lifts: CanonicalLift[];
}

/**
 * "My records" (lot C, D64): the records board of the user, one tile per lift (1RM, else the estimated
 * 1RM, with the recent gain), filtered by family, the CrossFit Total and the lifts not tested yet. A tile
 * opens the lift (`/training/records/:movementId`).
 */
@Component({
  selector: 'app-my-records',
  standalone: true,
  imports: [RouterLink, TranslatePipe, HlmBadge, HlmButton, HlmSpinner, PageComponent],
  templateUrl: './my-records.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MyRecordsComponent {
  // --- INJECTIONS ---
  private readonly recordService = inject(RecordService);
  protected readonly unitService = inject(UnitService);
  private readonly translate = inject(TranslateService);

  // --- STATE (SIGNALS & VARIABLES) ---
  readonly families = LIFT_FAMILIES;
  readonly family = signal<LiftFamily | null>(null);
  readonly records = signal<MovementRecords[]>([]);
  readonly lifts = signal<CanonicalLift[]>([]);
  readonly isLoading = signal<boolean>(true);
  readonly failed = signal<boolean>(false);

  // --- COMPUTED STATE ---
  readonly tiles = computed<LiftTile[]>(() => this.records()
    .map(r => ({
      movementId: r.movementId, movementName: r.movementName, family: familyOf(r.category),
      summary: liftSummary(r.variants.find(v => v.base))
    }))
    .filter((t): t is LiftTile => t.summary !== null));
  readonly shownTiles = computed(() => this.tiles().filter(t => this.family() === null || t.family === this.family()));
  /** Families that have tiles, so no chip is empty. */
  readonly usedFamilies = computed(() => this.families.filter(f => this.tiles().some(t => t.family === f)));
  readonly untested = computed<UntestedLift[]>(() => {
    const done = new Set(this.tiles().map(t => t.movementId));
    const rows = new Map<number, UntestedLift>();
    for (const lift of this.lifts()) {
      if (done.has(lift.movementId)) continue;
      const row = rows.get(lift.movementId) ?? {movementId: lift.movementId, movementName: lift.movementName, lifts: []};
      row.lifts.push(lift);
      rows.set(lift.movementId, row);
    }
    return [...rows.values()];
  });
  readonly total = computed(() => crossfitTotal(this.records()));
  readonly lang = computed(() => this.translate.currentLang() ?? this.translate.fallbackLang() ?? 'en');

  // --- LIFECYCLE ---
  constructor() {
    this.load();
  }

  // --- DATA FETCHING ---
  load(): void {
    const context = new HttpContext().set(BYPASS_LOADER, true);
    this.isLoading.set(true);
    forkJoin([this.recordService.getMyRecords(context), this.recordService.getCanonicalLifts(context)]).subscribe({
      next: ([records, lifts]) => {
        this.records.set(records);
        this.lifts.set(lifts);
        this.failed.set(false);
        this.isLoading.set(false);
      },
      error: () => {
        this.failed.set(true);
        this.isLoading.set(false);
      }
    });
  }

  // --- ACTIONS & EVENTS ---
  onFamily(family: LiftFamily | null): void {
    this.family.set(family);
  }

  // --- HELPERS ---
  /** Rounded load without its unit, as written on a whiteboard ("140"). */
  value(kg: number, estimated: boolean): string {
    const text = estimated ? this.unitService.formatPercentLoad(kg) : this.unitService.formatScoreLoad(kg, 'KG');
    return text.replace(/\s*\S+$/, '');
  }

  gain(summary: LiftSummary): string {
    return summary.gainKg ? this.value(summary.gainKg, false) : '';
  }

  day(date: string): string {
    return new Date(`${date}T00:00:00`).toLocaleDateString(this.lang(), {day: 'numeric', month: 'short'});
  }
}
