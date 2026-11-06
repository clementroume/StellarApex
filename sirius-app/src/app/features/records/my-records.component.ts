import {ChangeDetectionStrategy, Component, computed, inject, signal} from '@angular/core';
import {HttpContext} from '@angular/common/http';
import {TranslatePipe} from '@ngx-translate/core';
import {HlmCardImports} from '@spartan-ng/helm/card';
import {HlmSpinner} from '@spartan-ng/helm/spinner';
import {forkJoin} from 'rxjs';
import {CanonicalLift, MovementRecords} from '../../api/aldebaran/models/record.model';
import {RecordService} from '../../api/aldebaran/services/record.service';
import {BYPASS_LOADER} from '../../core/interceptors/loading.interceptor';
import {UnitService} from '../../core/services/unit.service';
import {PageComponent} from '../../shared/component/page/page.component';
import {MovementRecordsCardComponent} from './movement-records-card.component';
import {crossfitTotal} from './record.utils';

/** One movement of the page: the user's records and the canonical tests of it. */
interface RecordRow {
  movementId: number;
  movementName: string;
  records?: MovementRecords;
  lifts: CanonicalLift[];
}

/**
 * "My records" (lot C): every movement record of the user (sets and N RM tests, D47), grouped by
 * movement with the base version first, the CrossFit Total computed from the 1RMs (D53), and the
 * canonical N RM tests not done yet.
 */
@Component({
  selector: 'app-my-records',
  standalone: true,
  imports: [TranslatePipe, HlmCardImports, HlmSpinner, PageComponent, MovementRecordsCardComponent],
  templateUrl: './my-records.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MyRecordsComponent {
  // --- INJECTIONS ---
  private readonly recordService = inject(RecordService);
  protected readonly unitService = inject(UnitService);

  // --- STATE (SIGNALS & VARIABLES) ---
  readonly records = signal<MovementRecords[]>([]);
  readonly lifts = signal<CanonicalLift[]>([]);
  readonly isLoading = signal<boolean>(true);
  readonly failed = signal<boolean>(false);

  // --- COMPUTED STATE ---
  /** Movements with records, by name. */
  readonly withRecords = computed<RecordRow[]>(() => this.records().map(r => ({
    movementId: r.movementId, movementName: r.movementName, records: r,
    lifts: this.lifts().filter(l => l.movementId === r.movementId)
  })));
  /** Canonical lifts of movements without any record yet. */
  readonly toTest = computed<RecordRow[]>(() => {
    const done = new Set(this.records().map(r => r.movementId));
    const rows = new Map<number, RecordRow>();
    for (const lift of this.lifts()) {
      if (done.has(lift.movementId)) continue;
      const row = rows.get(lift.movementId) ?? {movementId: lift.movementId, movementName: lift.movementName, lifts: []};
      row.lifts.push(lift);
      rows.set(lift.movementId, row);
    }
    return [...rows.values()];
  });
  readonly total = computed(() => crossfitTotal(this.records()));

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
}
