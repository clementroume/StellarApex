import {ChangeDetectionStrategy, Component, computed, DestroyRef, inject, OnInit, signal} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {RouterModule} from '@angular/router';
import {HttpContext} from '@angular/common/http';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {NgIcon} from '@ng-icons/core';
import {debounceTime, distinctUntilChanged, forkJoin, Subject} from 'rxjs';
import {HlmBadge} from '@spartan-ng/helm/badge';
import {HlmButton} from '@spartan-ng/helm/button';
import {WodFacetFilters, WodListFilters, WodService} from '../../../api/aldebaran/services/wod.service';
import {WodSummaryResponse} from '../../../api/aldebaran/models/wod.model';
import {AccessService} from '../../../core/services/access.service';
import {ExporterService} from '../../../api/aldebaran/services/exporter.service';
import {BYPASS_LOADER} from '../../../core/interceptors/loading.interceptor';
import {SearchInputComponent} from '../../../shared/component/search-input/search-input.component';
import {ListStateComponent} from '../../../shared/component/list/list-state/list-state.component';
import {formatTimeCapShort} from '../wod.utils';

export const WOD_PAGE_SIZE = 20;
export const WOD_SEARCH_DEBOUNCE_MS = 300;
/** Families of official competitions: latest edition first, events in order within an edition. */
export const COMPETITION_CATEGORIES = ['OPEN', 'QUARTERFINALS', 'SEMIFINALS', 'GAMES'];
/** Family chips in this order (Aldebaran counts them). */
const FAMILY_ORDER = ['GIRLS', 'HERO', 'BENCHMARK', 'OPEN', 'QUARTERFINALS', 'SEMIFINALS', 'GAMES'];
/** Movements shown in the preview of a row. */
const PREVIEW_MOVEMENTS = 4;

/** A run of rows under an optional heading ("Open 2026"). */
export interface WodGroup {
  heading: string | null;
  wods: WodSummaryResponse[];
}

/**
 * Edition of a competition WOD from its title: "Open 26.1" → 2026, "Games 2025 - Atlas" → 2025,
 * or null.
 */
export function editionYear(title: string): number | null {
  const full = /\b(20\d{2})\b/.exec(title);
  if (full) return Number(full[1]);
  const open = /\b(\d{2})\.\d/.exec(title);
  return open ? 2000 + Number(open[1]) : null;
}

/**
 * Catalogue of the public WODs (Girls, Heroes, benchmarks, Open...): family chips with their
 * counts, format and sub-category chips, rows with a preview of the movements, competitions
 * grouped by edition (latest first, events in order), numbered pagination. Admins create them; box
 * programming goes through sessions, personal WODs live in My training.
 */
@Component({
  selector: 'app-wod-list',
  standalone: true,
  imports: [HlmButton, HlmBadge, RouterModule, TranslatePipe, NgIcon, SearchInputComponent, ListStateComponent],
  templateUrl: './wod-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WodListComponent implements OnInit {
  // --- INJECTIONS ---
  private readonly wodService = inject(WodService);
  private readonly translate = inject(TranslateService);
  private readonly accessService = inject(AccessService);
  private readonly exporterService = inject(ExporterService);
  private readonly destroyRef = inject(DestroyRef);

  // --- STATE (SIGNALS & VARIABLES) ---
  isLoading = signal<boolean>(false);
  isExporting = signal<boolean>(false);
  wods = signal<WodSummaryResponse[]>([]);
  /** Number of WODs for the current filters (pages). */
  total = signal<number>(0);
  // Formats, sub-categories and family counts present for the current search (Aldebaran facets)
  wodTypes = signal<string[]>([]);
  structures = signal<string[]>([]);
  categoryCounts = signal<Record<string, number>>({});
  allCount = signal<number>(0);
  private readonly searchTrigger$ = new Subject<string>();

  // UI State persisted in service
  searchQuery = this.wodService.savedSearchQuery;
  typeFilter = this.wodService.savedTypeFilter;
  structureFilter = this.wodService.savedStructureFilter;
  categoryFilter = this.wodService.savedCategoryFilter;
  page = this.wodService.savedPage;

  // --- COMPUTED STATE ---
  isAdmin = this.accessService.isAdmin;

  hasActiveFilter = computed(() =>
    !!this.searchQuery().trim() || !!this.typeFilter() || !!this.categoryFilter());

  /** Family chips present, in a fixed order, with their counts. */
  families = computed(() => {
    const counts = this.categoryCounts();
    return FAMILY_ORDER.filter(c => (counts[c] ?? 0) > 0).map(c => ({category: c, count: counts[c]}));
  });

  pageCount = computed(() => Math.max(1, Math.ceil(this.total() / WOD_PAGE_SIZE)));

  /** Page numbers shown: first, last, and two around the current one; null = ellipsis. */
  pageNumbers = computed<(number | null)[]>(() => {
    const count = this.pageCount();
    const current = this.page();
    const shown = new Set([0, count - 1, current - 1, current, current + 1].filter(p => p >= 0 && p < count));
    const sorted = [...shown].sort((a, b) => a - b);
    const result: (number | null)[] = [];
    sorted.forEach((p, i) => {
      if (i > 0 && p - sorted[i - 1] > 1) result.push(null);
      result.push(p);
    });
    return result;
  });

  /** "1–20" of the current page. */
  range = computed(() => {
    const from = this.total() === 0 ? 0 : this.page() * WOD_PAGE_SIZE + 1;
    return {from, to: Math.min(this.total(), (this.page() + 1) * WOD_PAGE_SIZE), total: this.total()};
  });

  /**
   * Rows of the page: competitions grouped by edition, latest first and events in order inside an
   * edition (26.1, 26.2, 26.3); other families A to Z without heading.
   */
  groups = computed<WodGroup[]>(() => {
    const wods = this.wods();
    if (!COMPETITION_CATEGORIES.includes(this.categoryFilter())) return [{heading: null, wods}];
    const byYear = new Map<number | null, WodSummaryResponse[]>();
    for (const wod of wods) {
      const year = editionYear(wod.title);
      byYear.set(year, [...(byYear.get(year) ?? []), wod]);
    }
    const family = this.translate.instant('WOD.CATEGORIES.' + this.categoryFilter());
    return [...byYear.entries()]
      .sort(([a], [b]) => (b ?? 0) - (a ?? 0))
      .map(([year, list]) => ({
        heading: year ? `${family} ${year}` : family,
        wods: [...list].sort((a, b) => a.title.localeCompare(b.title, undefined, {numeric: true}))
      }));
  });

  // --- LIFECYCLE ---
  ngOnInit(): void {
    this.wodService.refreshNeeded$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.reload());

    this.searchTrigger$
      .pipe(debounceTime(WOD_SEARCH_DEBOUNCE_MS), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.page.set(0);
        this.reload();
      });

    this.reload();
  }

  // --- DATA FETCHING ---
  reload(): void {
    this.loadPage();
    this.loadFacets();
  }

  /** The current page and the number of WODs for the filters. */
  loadPage(): void {
    const filters = this.filters();
    const context = new HttpContext().set(BYPASS_LOADER, true);
    const sort = COMPETITION_CATEGORIES.includes(this.categoryFilter()) ? 'title,desc' : 'title,asc';
    this.isLoading.set(true);
    forkJoin({
      slice: this.wodService.getWods(filters, this.page(), WOD_PAGE_SIZE, context, sort),
      total: this.wodService.countWods(filters, context)
    }).subscribe({
      next: ({slice, total}) => {
        this.wods.set(slice.content);
        this.total.set(total);
        this.isLoading.set(false);
      },
      error: () => {
        this.wods.set([]);
        this.total.set(0);
        this.isLoading.set(false);
      }
    });
  }

  /** Formats, sub-categories of the chosen format and family counts for the current search. */
  loadFacets(): void {
    const filters: WodFacetFilters = {
      scope: 'PUBLIC',
      search: this.searchQuery().trim() || undefined,
      type: this.typeFilter() || undefined
    };

    this.wodService.getFacets(filters, new HttpContext().set(BYPASS_LOADER, true)).subscribe({
      next: facets => {
        this.wodTypes.set(facets.wodTypes);
        this.structures.set(facets.structures ?? []);
        this.categoryCounts.set(facets.categoryCounts ?? {});
        this.allCount.set(facets.total ?? 0);
        // A selected value that no longer returns anything would leave an empty list behind
        const staleType = !!this.typeFilter() && !facets.wodTypes.includes(this.typeFilter());
        const staleStructure = !!this.structureFilter() && !(facets.structures ?? []).includes(this.structureFilter());
        const staleCategory = !!this.categoryFilter() && !(facets.categories ?? []).includes(this.categoryFilter());
        if (staleType) this.typeFilter.set('');
        if (staleStructure || staleType) this.structureFilter.set('');
        if (staleCategory) this.categoryFilter.set('');
        if (staleType || staleStructure || staleCategory) {
          this.page.set(0);
          this.loadPage();
        }
      },
      error: () => {
        this.wodTypes.set([]);
        this.structures.set([]);
        this.categoryCounts.set({});
      }
    });
  }

  private filters(): WodListFilters {
    return {
      scope: 'PUBLIC',
      search: this.searchQuery().trim() || undefined,
      type: this.typeFilter() || undefined,
      structure: this.structureFilter() || undefined,
      category: this.categoryFilter() || undefined
    };
  }

  // --- ACTIONS & EVENTS ---
  onSearchChange(query: string): void {
    this.searchQuery.set(query);
    this.searchTrigger$.next(query.trim());
  }

  onCategoryChange(category: string): void {
    if (this.categoryFilter() === category) return;
    this.categoryFilter.set(category);
    this.page.set(0);
    this.loadPage();
  }

  /** A format resets its sub-category and reloads the sub-categories present. */
  onTypeChange(type: string): void {
    if (this.typeFilter() === type) return;
    this.typeFilter.set(type);
    this.structureFilter.set('');
    this.page.set(0);
    this.reload();
  }

  onStructureChange(structure: string): void {
    this.structureFilter.set(this.structureFilter() === structure ? '' : structure);
    this.page.set(0);
    this.loadPage();
  }

  goToPage(page: number): void {
    if (page < 0 || page >= this.pageCount() || page === this.page()) return;
    this.page.set(page);
    this.loadPage();
    if (typeof window !== 'undefined') window.scrollTo({top: 0, behavior: 'smooth'});
  }

  /** Temporary dev backup of the public WODs to the git-ignored seed CSVs. */
  exportCsv(): void {
    this.exporterService.executeCsvExport(this.exporterService.exportWods(), this.isExporting);
  }

  // --- HELPERS ---
  /** "Thruster · Pull-up · Run +2". */
  preview(wod: WodSummaryResponse): string {
    const names = wod.movementNames ?? [];
    const shown = names.slice(0, PREVIEW_MOVEMENTS).join(' · ');
    return names.length > PREVIEW_MOVEMENTS ? `${shown} +${names.length - PREVIEW_MOVEMENTS}` : shown;
  }

  /** "Cap 12'", "21-15-9", or nothing. */
  detail(wod: WodSummaryResponse): string {
    const parts: string[] = [];
    if (wod.repScheme && /^\d+(-\d+)+$/.test(wod.repScheme.trim())) parts.push(wod.repScheme.trim());
    if (wod.timeCapSeconds) {
      const key = wod.wodType === 'AMRAP' ? 'WOD.FIELDS.DURATION_SHORT' : 'WOD.FIELDS.TIME_CAP_SHORT';
      parts.push(`${this.translate.instant(key)} ${formatTimeCapShort(wod.timeCapSeconds)}`);
    }
    return parts.join(' · ');
  }
}
