import {ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked} from '@angular/core';
import {HttpContext} from '@angular/common/http';
import {NgTemplateOutlet} from '@angular/common';
import {RouterLink} from '@angular/router';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {HlmBadge} from '@spartan-ng/helm/badge';
import {HlmSpinner} from '@spartan-ng/helm/spinner';
import {CanonicalLift, MovementRecords, RecordPoint, VariantRecords, RecordEntry} from '../../api/aldebaran/models/record.model';
import {RecordService} from '../../api/aldebaran/services/record.service';
import {BYPASS_LOADER} from '../../core/interceptors/loading.interceptor';
import {UnitService} from '../../core/services/unit.service';
import {PageComponent} from '../../shared/component/page/page.component';
import {PERCENT_STEPS, percentBase, rmRows, sourceLabel, variantLabel} from './record.utils';

/** Bars of the progression chart (the latest bests). */
const PROGRESS_BARS = 12;

/**
 * Records of one lift (lot C, D63-D64): the N RM table of the base version (estimates muted for the
 * usual N RM without a record), the % table of the 1RM, the variants, the 1RM progression and the
 * canonical tests. A page (`/training/records/:movementId`, route data `page`) or a block of the
 * movement page.
 */
@Component({
  selector: 'app-lift-records',
  standalone: true,
  imports: [NgTemplateOutlet, RouterLink, TranslatePipe, HlmBadge, HlmSpinner, PageComponent],
  templateUrl: './lift-records.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LiftRecordsComponent {
  // --- INJECTIONS ---
  private readonly recordService = inject(RecordService);
  private readonly translate = inject(TranslateService);
  protected readonly unitService = inject(UnitService);
  /** The movement (route parameter on the page). */
  readonly movementId = input.required<number | string>();
  /** Known name (movement page); the page takes it from the records or the tests. */
  readonly movementName = input<string>();
  /** Route data: shown as a page of My training. */
  readonly page = input<boolean>(false);
  /** Variant shown (query parameter on the page, the chosen variant on a movement page). */
  readonly variant = input<string | null | undefined>('');

  // --- STATE (SIGNALS & VARIABLES) ---
  readonly percentSteps = PERCENT_STEPS;
  readonly records = signal<MovementRecords | undefined>(undefined);
  readonly lifts = signal<CanonicalLift[]>([]);
  readonly progress = signal<RecordPoint[]>([]);
  readonly isLoading = signal<boolean>(true);

  // --- COMPUTED STATE ---
  readonly id = computed(() => Number(this.movementId()));
  readonly lang = computed(() => this.translate.currentLang() ?? this.translate.fallbackLang() ?? 'en');
  readonly key = computed(() => this.variant() ?? '');
  readonly name = computed(() => this.movementName() ?? this.lifts().find(l => l.variantKey === this.key())?.usualNameEn
    ?? this.base()?.usualNameEn ?? this.records()?.movementName ?? this.lifts()[0]?.movementName ?? '');
  /** The variant the page is about. */
  readonly base = computed<VariantRecords | undefined>(() => this.records()?.variants.find(v => v.variantKey === this.key()));
  readonly variants = computed<VariantRecords[]>(() => this.records()?.variants.filter(v => v.variantKey !== this.key()) ?? []);
  readonly rows = computed(() => rmRows(this.base()));
  /** Base of the % table: the real 1RM, else the estimated one (same rule as the whiteboard). */
  readonly oneRm = computed(() => {
    const records = this.records();
    return percentBase(records ? [records] : [], this.id(), 1, this.key());
  });
  readonly bars = computed(() => {
    const points = this.progress().slice(-PROGRESS_BARS);
    if (points.length < 2) return [];
    const max = Math.max(...points.map(p => p.weightKg));
    const min = Math.min(...points.map(p => p.weightKg)) * 0.9;
    return points.map((p, i) => ({
      ...p, last: i === points.length - 1, height: Math.round(20 + 80 * (p.weightKg - min) / (max - min || 1))
    }));
  });

  // --- LIFECYCLE ---
  constructor() {
    effect(() => {
      const id = this.id();
      const key = this.key();
      this.recordService.version();
      if (id && !Number.isNaN(id)) untracked(() => this.fetch(id, key));
    });
  }

  // --- DATA FETCHING ---
  private fetch(id: number, key: string): void {
    const context = new HttpContext().set(BYPASS_LOADER, true);
    // A later pick (movement, variant) is already loading: drop this answer
    const isCurrent = () => this.id() === id && this.key() === key;
    this.isLoading.set(true);
    this.recordService.getMyMovementRecords(id, context).subscribe({
      next: records => {
        if (!isCurrent()) return;
        this.records.set(records[0]);
        this.isLoading.set(false);
      },
      error: () => {
        if (!isCurrent()) return;
        this.records.set(undefined);
        this.isLoading.set(false);
      }
    });
    this.recordService.getCanonicalLifts(context).subscribe({
      next: lifts => {
        if (isCurrent()) this.lifts.set(lifts.filter(l => l.movementId === id && l.variantKey === key));
      },
      error: () => {
        if (isCurrent()) this.lifts.set([]);
      }
    });
    this.recordService.getMyProgress(id, 1, context, key).subscribe({
      next: points => {
        if (isCurrent()) this.progress.set(points);
      },
      error: () => {
        if (isCurrent()) this.progress.set([]);
      }
    });
  }

  // --- HELPERS ---
  load(kg: number): string {
    return this.unitService.formatScoreLoad(kg, 'KG');
  }

  estimate(kg: number): string {
    return this.unitService.formatPercentLoad(kg);
  }

  day(date: string): string {
    return new Date(`${date}T00:00:00`).toLocaleDateString(this.lang(), {day: 'numeric', month: 'short', year: 'numeric'});
  }

  shortDay(date: string): string {
    return new Date(`${date}T00:00:00`).toLocaleDateString(this.lang(), {month: 'short', year: '2-digit'});
  }

  label(v: VariantRecords): string {
    return variantLabel(v, key => this.translate.instant(key));
  }

  /** What an inherited record really is ("Clean · Power"). */
  source(record: RecordEntry): string {
    return record.inheritedFrom ? sourceLabel(record.inheritedFrom, key => this.translate.instant(key)) : '';
  }
}
