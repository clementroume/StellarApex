import {ChangeDetectionStrategy, Component, computed, DestroyRef, inject, OnInit, signal} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {Params, RouterModule} from '@angular/router';
import {HttpContext} from '@angular/common/http';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {NgIcon} from '@ng-icons/core';
import {debounceTime, distinctUntilChanged, Subject} from 'rxjs';
import {WodFacetFilters, WodListFilters, WodService} from '../../../api/aldebaran/services/wod.service';
import {WodScope, WodSummaryResponse} from '../../../api/aldebaran/models/wod.model';
import {AccessService} from '../../../core/services/access.service';
import {ExporterService} from '../../../api/aldebaran/services/exporter.service';
import {AdminService} from '../../../api/antares/services/admin.service';
import {UserSummary} from '../../../api/antares/models/user.model';
import {BYPASS_LOADER} from '../../../core/interceptors/loading.interceptor';
import {SearchInputComponent} from '../../../shared/component/search-input/search-input.component';
import {ListStateComponent} from '../../../shared/component/list/list-state/list-state.component';
import {ListItemComponent} from '../../../shared/component/list/list-item/list-item.component';
import {formatTimeCapShort, wodTypeLabel} from '../wod.utils';

export const WOD_PAGE_SIZE = 20;
export const WOD_SEARCH_DEBOUNCE_MS = 300;

export interface WodScopeTab {
  key: string;
  // Translation key, or the raw gym name for gym tabs
  label: string;
  translate: boolean;
  gymId?: number;
}

/** Where the "create" button leads, depending on the current tab. */
export interface WodCreateTarget {
  labelKey: string;
  gymName?: string;
  queryParams: Params;
}

import {HlmSpinner} from '@spartan-ng/helm/spinner';
import {NativeSelectDirective} from '../../../shared/component/native-select/native-select.directive';
import {HlmBadge} from '@spartan-ng/helm/badge';
import {HlmButton} from '@spartan-ng/helm/button';
/** Gym names shown to admins in the WOD list (Antares caps a page at 50). */
const ADMIN_GYM_PAGE_SIZE = 50;

@Component({
  selector: 'app-wod-list',
  standalone: true,
  imports: [HlmButton, HlmBadge, NativeSelectDirective, HlmSpinner, RouterModule, TranslatePipe, NgIcon, SearchInputComponent, ListStateComponent, ListItemComponent],
  templateUrl: './wod-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WodListComponent implements OnInit {
  // --- INJECTIONS ---
  private readonly wodService = inject(WodService);
  private readonly translate = inject(TranslateService);
  private readonly accessService = inject(AccessService);
  private readonly exporterService = inject(ExporterService);
  private readonly adminService = inject(AdminService);
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
  // Admin selectors of the "All" tab
  adminAuthors = signal<UserSummary[]>([]);
  private readonly facetGymIds = signal<number[]>([]);
  private readonly gymNames = signal<Map<number, string>>(new Map());
  private currentPage = 0;
  private readonly searchTrigger$ = new Subject<string>();

  // UI State persisted in service
  searchQuery = this.wodService.savedSearchQuery;
  typeFilter = this.wodService.savedTypeFilter;
  categoryFilter = this.wodService.savedCategoryFilter;
  private readonly savedScopeTab = this.wodService.savedScopeTab;
  adminGymId = this.wodService.savedAdminGymId;
  adminAuthorId = this.wodService.savedAdminAuthorId;

  // --- COMPUTED STATE ---
  isAdmin = this.accessService.isAdmin;

  scopeTabs = computed<WodScopeTab[]>(() => [
    {key: 'ALL', label: 'WOD.SCOPES.ALL', translate: true},
    ...this.accessService.memberships().map(m => ({key: `GYM:${m.gymId}`, label: m.gymName, translate: false, gymId: m.gymId})),
    {key: 'MINE', label: 'WOD.SCOPES.MINE', translate: true},
    {key: 'PUBLIC', label: 'WOD.SCOPES.PUBLIC', translate: true}
  ]);

  /** Defaults to the first gym: the gym programming matters more than the public catalog. */
  activeScopeTab = computed<WodScopeTab>(() => {
    const tabs = this.scopeTabs();
    return tabs.find(t => t.key === this.savedScopeTab()) ?? tabs.find(t => t.gymId !== undefined) ?? tabs[0];
  });

  adminGyms = computed(() => {
    const names = this.gymNames();
    return this.facetGymIds()
      .map(id => ({id, name: names.get(id) ?? `#${id}`}))
      .sort((a, b) => a.name.localeCompare(b.name));
  });

  showAdminFilters = computed(() => this.isAdmin() && this.activeScopeTab().key === 'ALL');

  hasActiveFilter = computed(() =>
    !!this.searchQuery().trim() || !!this.typeFilter() || !!this.categoryFilter()
    || this.adminGymId() !== null || this.adminAuthorId() !== null
  );

  /** Filters of the list and facets, the admin selectors only applying to the "All" tab. */
  private readonly baseFilters = computed<WodFacetFilters>(() => {
    const tab = this.activeScopeTab();
    if (tab.gymId !== undefined) return {scope: 'GYM', gymId: tab.gymId};
    if (this.showAdminFilters() && this.adminGymId() !== null) return {scope: 'GYM', gymId: this.adminGymId()!};
    if (this.showAdminFilters() && this.adminAuthorId() !== null) return {scope: 'ALL', authorId: this.adminAuthorId()!};
    return {scope: tab.key as WodScope};
  });

  /** Gym tabs where the user programs WODs create gym WODs; elsewhere the button says "personal". */
  createTarget = computed<WodCreateTarget>(() => {
    const tab = this.activeScopeTab();
    if (tab.gymId !== undefined) {
      return this.accessService.hasGymPermission(tab.gymId, 'WOD_WRITE')
        ? {labelKey: 'WOD.ACTIONS.ADD_FOR_GYM', gymName: tab.label, queryParams: {gymId: tab.gymId}}
        : {labelKey: 'WOD.ACTIONS.ADD_PERSONAL', queryParams: {visibility: 'PRIVATE'}};
    }
    if (tab.key === 'MINE') {
      return {labelKey: 'WOD.ACTIONS.ADD_PERSONAL', queryParams: {visibility: 'PRIVATE'}};
    }
    return {labelKey: 'WOD.ACTIONS.ADD', queryParams: {}};
  });

  // --- LIFECYCLE ---
  ngOnInit(): void {
    this.wodService.refreshNeeded$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.reload());

    this.searchTrigger$
      .pipe(debounceTime(WOD_SEARCH_DEBOUNCE_MS), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.reload());

    if (this.isAdmin()) {
      this.adminService.searchGyms('', null, 0, ADMIN_GYM_PAGE_SIZE)
        .subscribe(slice => this.gymNames.set(new Map(slice.content.map(g => [g.id, g.name]))));
    }
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

  /** Formats and categories present for the current filters, plus the admin selector options. */
  loadFacets(): void {
    const filters: WodFacetFilters = {...this.baseFilters(), search: this.searchQuery().trim() || undefined};

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
        if (this.isAdmin()) {
          this.updateAdminOptions(facets.gymIds, facets.authorIds);
        }
      },
      error: () => {
        this.wodTypes.set([]);
        this.categories.set([]);
      }
    });
  }

  private updateAdminOptions(gymIds: number[], authorIds: number[]): void {
    this.facetGymIds.set(gymIds);

    const known = new Set(this.adminAuthors().map(a => a.id));
    if (authorIds.length === known.size && authorIds.every(id => known.has(id))) return;
    if (authorIds.length === 0) {
      this.adminAuthors.set([]);
      return;
    }
    this.adminService.lookupUsers(authorIds).subscribe(authors => this.adminAuthors.set(authors));
  }

  private fetchPage(page: number, onSuccess: (wods: WodSummaryResponse[]) => void, onError: () => void): void {
    const filters: WodListFilters = {
      ...this.baseFilters(),
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

  onScopeChange(key: string): void {
    if (this.activeScopeTab().key === key) return;
    this.savedScopeTab.set(key);
    this.reload();
  }

  /** Admin "All" tab: a gym and an athlete are alternative views, never combined. */
  onAdminGymChange(value: string): void {
    this.adminGymId.set(value ? Number(value) : null);
    this.adminAuthorId.set(null);
    this.reload();
  }

  onAdminAuthorChange(value: string): void {
    this.adminAuthorId.set(value ? Number(value) : null);
    this.adminGymId.set(null);
    this.reload();
  }

  /** Temporary dev backup of the public WODs to the git-ignored seed CSVs. */
  exportCsv(): void {
    this.exporterService.executeCsvExport(this.exporterService.exportWods(), this.isExporting);
  }

  // --- HELPERS ---
  formatTimeCap(seconds?: number): string {
    return formatTimeCapShort(seconds);
  }

  /** Gym name for a gym WOD (member gyms, or every gym for an admin). */
  gymName(gymId?: number): string | null {
    if (gymId === undefined) return null;
    return this.accessService.memberships().find(m => m.gymId === gymId)?.gymName
      ?? this.gymNames().get(gymId)
      ?? null;
  }
}
