import {ChangeDetectionStrategy, Component, computed, DestroyRef, inject, OnInit, signal} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {RouterModule} from '@angular/router';
import {HttpContext} from '@angular/common/http';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {NgIcon} from '@ng-icons/core';
import {debounceTime, distinctUntilChanged, Subject} from 'rxjs';
import {WodFacetFilters, WodListFilters, WodService} from '../../../api/aldebaran/services/wod.service';
import {WodSummaryResponse} from '../../../api/aldebaran/models/wod.model';
import {AccessService} from '../../../core/services/access.service';
import {ExporterService} from '../../../api/aldebaran/services/exporter.service';
import {BYPASS_LOADER} from '../../../core/interceptors/loading.interceptor';
import {SearchInputComponent} from '../../../shared/component/search-input/search-input.component';
import {ListStateComponent} from '../../../shared/component/list/list-state/list-state.component';
import {ListItemComponent} from '../../../shared/component/list/list-item/list-item.component';
import {formatTimeCapShort, wodTypeLabel} from '../wod.utils';

export const WOD_PAGE_SIZE = 20;
export const WOD_SEARCH_DEBOUNCE_MS = 300;


import {HlmSpinner} from '@spartan-ng/helm/spinner';
import {HlmBadge} from '@spartan-ng/helm/badge';
import {HlmButton} from '@spartan-ng/helm/button';
/**
 * Catalogue of the public WODs (Girls, Hero, CrossFit benchmarks, Open...), filtered by category and
 * format; admins create them. Box programming goes through sessions, personal WODs live in My
 * training.
 */
@Component({
  selector: 'app-wod-list',
  standalone: true,
  imports: [HlmButton, HlmBadge, HlmSpinner, RouterModule, TranslatePipe, NgIcon, SearchInputComponent, ListStateComponent, ListItemComponent],
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
  isLoadingMore = signal<boolean>(false);
  isExporting = signal<boolean>(false);
  wods = signal<WodSummaryResponse[]>([]);
  hasMore = signal<boolean>(false);
  // Formats and categories present for the current filters (Aldebaran facets): no empty chip
  wodTypes = signal<string[]>([]);
  categories = signal<string[]>([]);
  private currentPage = 0;
  private readonly searchTrigger$ = new Subject<string>();

  // UI State persisted in service
  searchQuery = this.wodService.savedSearchQuery;
  typeFilter = this.wodService.savedTypeFilter;
  categoryFilter = this.wodService.savedCategoryFilter;

  // --- COMPUTED STATE ---
  isAdmin = this.accessService.isAdmin;

  hasActiveFilter = computed(() =>
    !!this.searchQuery().trim() || !!this.typeFilter() || !!this.categoryFilter());

  // --- LIFECYCLE ---
  ngOnInit(): void {
    this.wodService.refreshNeeded$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.reload());

    this.searchTrigger$
      .pipe(debounceTime(WOD_SEARCH_DEBOUNCE_MS), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.reload());

    this.reload();
  }

  // --- DATA FETCHING ---
  reload(): void {
    this.loadWods();
    this.loadFacets();
  }

  loadWods(): void {
    this.currentPage = 0;
    this.isLoading.set(true);
    this.fetchPage(0, wods => {
      this.wods.set(wods);
      this.isLoading.set(false);
    }, () => {
      this.wods.set([]);
      this.isLoading.set(false);
    });
  }

  loadMore(): void {
    if (!this.hasMore() || this.isLoadingMore()) return;
    const nextPage = this.currentPage + 1;
    this.isLoadingMore.set(true);
    this.fetchPage(nextPage, wods => {
      this.currentPage = nextPage;
      this.wods.update(current => [...current, ...wods]);
      this.isLoadingMore.set(false);
    }, () => this.isLoadingMore.set(false));
  }

  /** Formats and categories present for the current filters. */
  loadFacets(): void {
    const filters: WodFacetFilters = {scope: 'PUBLIC', search: this.searchQuery().trim() || undefined};

    this.wodService.getFacets(filters, new HttpContext().set(BYPASS_LOADER, true)).subscribe({
      next: facets => {
        this.wodTypes.set(facets.wodTypes);
        this.categories.set(facets.categories ?? []);
        // A selected value that no longer returns anything would leave an empty list behind
        const staleType = !!this.typeFilter() && !facets.wodTypes.includes(this.typeFilter());
        const staleCategory = !!this.categoryFilter() && !(facets.categories ?? []).includes(this.categoryFilter());
        if (staleType) this.typeFilter.set('');
        if (staleCategory) this.categoryFilter.set('');
        if (staleType || staleCategory) this.loadWods();
      },
      error: () => {
        this.wodTypes.set([]);
        this.categories.set([]);
      }
    });
  }

  private fetchPage(page: number, onSuccess: (wods: WodSummaryResponse[]) => void, onError: () => void): void {
    const filters: WodListFilters = {
      scope: 'PUBLIC',
      search: this.searchQuery().trim() || undefined,
      type: this.typeFilter() || undefined,
      category: this.categoryFilter() || undefined
    };

    this.wodService.getWods(filters, page, WOD_PAGE_SIZE, new HttpContext().set(BYPASS_LOADER, true))
      .subscribe({
        next: slice => {
          this.hasMore.set(!slice.last);
          onSuccess(slice.content);
        },
        error: () => {
          this.hasMore.set(false);
          onError();
        }
      });
  }

  // --- ACTIONS & EVENTS ---
  onSearchChange(query: string): void {
    this.searchQuery.set(query);
    this.searchTrigger$.next(query.trim());
  }

  onTypeChange(type: string): void {
    if (this.typeFilter() === type) return;
    this.typeFilter.set(type);
    this.loadWods();
  }

  onCategoryChange(category: string): void {
    if (this.categoryFilter() === category) return;
    this.categoryFilter.set(category);
    this.loadWods();
  }

  /** "Girls / For Time": the category (when any) then the format, as written on the whiteboard. */
  typeLabel(wod: Pick<WodSummaryResponse, 'wodType' | 'category'>): string {
    return wodTypeLabel(key => this.translate.instant(key), wod);
  }

  /** Temporary dev backup of the public WODs to the git-ignored seed CSVs. */
  exportCsv(): void {
    this.exporterService.executeCsvExport(this.exporterService.exportWods(), this.isExporting);
  }

  // --- HELPERS ---
  formatTimeCap(seconds?: number): string {
    return formatTimeCapShort(seconds);
  }
}
