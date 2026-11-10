import {afterRenderEffect, ChangeDetectionStrategy, Component, computed, ElementRef, inject, OnInit, signal, viewChild} from '@angular/core';
import {NgTemplateOutlet} from '@angular/common';
import {RouterModule} from '@angular/router';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {MuscleService} from '../../../api/aldebaran/services/muscle.service';
import {MuscleResponse} from '../../../api/aldebaran/models/muscle.model';
import {AccessService} from '../../../core/services/access.service';
import {NgIcon} from '@ng-icons/core';
import {ExporterService} from '../../../api/aldebaran/services/exporter.service';
import {HttpContext} from '@angular/common/http';
import {BYPASS_LOADER} from '../../../core/interceptors/loading.interceptor';
import {SearchInputComponent} from '../../../shared/component/search-input/search-input.component';
import {ListStateComponent} from '../../../shared/component/list/list-state/list-state.component';
import {ThemeService} from '../../../core/services/theme.service';
import {MuscleCreditComponent} from '../muscle-credit/muscle-credit.component';
import {muscleThumbUrl} from '../muscle-model/muscle-model';
import {MuscleSceneComponent} from '../muscle-scene/muscle-scene.component';

/** A row of a region: a top-level muscle (group or standalone) or a member shown under its group. */
export interface MuscleRow {
  muscle: MuscleResponse;
  isMember: boolean;
}

import {normalize} from '../../../shared/text.utils';
import {HlmButton} from '@spartan-ng/helm/button';
@Component({
  selector: 'app-muscle-list',
  standalone: true,
  imports: [MuscleCreditComponent, MuscleSceneComponent, NgTemplateOutlet, HlmButton, RouterModule, TranslatePipe, NgIcon, SearchInputComponent, ListStateComponent],
  templateUrl: './muscle-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    .head { display: flex; flex-direction: column; gap: 0.75rem; margin: 2rem 0 1rem; }
    .regions { display: flex; gap: 0.5rem; overflow-x: auto; scrollbar-width: none; }
    .regions button { flex: none; min-height: 2.75rem; }
    .explorer { display: grid; grid-template-columns: minmax(0, 1fr); gap: 1rem; align-items: start; }
    .row { display: flex; align-items: center; gap: 0.75rem; padding: 0.375rem 0; border-top: 1px solid var(--sa-base-300); }
    .thumb { flex: none; width: 4rem; height: 4rem; object-fit: contain; }
    .member { padding-left: 2rem; }
    @media (min-width: 768px) {
      .head { flex-direction: row; align-items: center; justify-content: space-between; gap: 1.5rem; }
      .search { width: 20rem; flex: none; }
      .regions { flex-wrap: wrap; overflow-x: visible; }
      .explorer { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 2rem; }
    }
  `
})
export class MuscleListComponent implements OnInit {
  // --- INJECTIONS ---
  private readonly muscleService = inject(MuscleService);
  private readonly accessService = inject(AccessService);
  private readonly translate = inject(TranslateService);
  private readonly exporterService = inject(ExporterService);
  private readonly themeService = inject(ThemeService);
  private readonly regions = viewChild<ElementRef<HTMLElement>>('regions');

  // --- STATE (SIGNALS & VARIABLES) ---
  isLoading = signal<boolean>(false);
  isExporting = signal<boolean>(false);
  activeLang = computed(() => this.translate.currentLang() ?? this.translate.fallbackLang() ?? 'en');

  rawMuscles = signal<MuscleResponse[]>([]);
  regionKeys = signal<string[]>([]);

  // UI State persisted in service
  searchQuery = this.muscleService.savedSearchQuery;
  activeRegion = this.muscleService.savedRegion;

  // --- COMPUTED STATE ---
  isAdmin = this.accessService.isAdmin;

  filteredMuscles = computed(() => {
    const query = normalize(this.searchQuery());
    const list = this.rawMuscles();
    if (!query) return list;
    return list.filter(m => normalize(m.medicalName).includes(query) || normalize(this.getLocalizedName(m)).includes(query));
  });

  /**
   * Rows per region: each top-level muscle (functional group or standalone) followed by its members.
   * A group stays visible when only one of its members matches the search.
   */
  groupedMuscles = computed(() => {
    const all = this.rawMuscles();
    const matched = new Set(this.filteredMuscles().map(m => m.id));
    const ids = new Set(all.map(m => m.id));
    const byName = (a: MuscleResponse, b: MuscleResponse) => this.getLocalizedName(a).localeCompare(this.getLocalizedName(b));
    const membersOf = new Map<number, MuscleResponse[]>();
    all.filter(m => m.parent && ids.has(m.parent.id)).forEach(m => {
      membersOf.set(m.parent!.id, [...(membersOf.get(m.parent!.id) ?? []), m]);
    });

    const grouped = new Map<string, MuscleRow[]>();
    this.regionKeys().forEach(g => grouped.set(g, []));
    all.filter(m => !m.parent || !ids.has(m.parent.id)).sort(byName).forEach(top => {
      const members = (membersOf.get(top.id) ?? []).filter(m => matched.has(m.id)).sort(byName);
      if (!matched.has(top.id) && members.length === 0) return;
      if (!grouped.has(top.region)) {
        grouped.set(top.region, []);
      }
      grouped.get(top.region)!.push(
        {muscle: top, isMember: false},
        ...members.map(muscle => ({muscle, isMember: true})));
    });
    return grouped;
  });

  readonly isSearching = computed(() => this.searchQuery().trim() !== '');

  /** The rows shown: every muscle matching a search, else the groups and standalone muscles of the region picked. */
  readonly shownRows = computed<MuscleRow[]>(() => this.isSearching()
    ? this.populatedGroups().flatMap(group => this.groupedMuscles().get(group) ?? [])
    : (this.groupedMuscles().get(this.activeRegion()) ?? []).filter(row => !row.isMember));

  populatedGroups = computed(() => {
    const groups = this.regionKeys();
    const map = this.groupedMuscles();
    return groups.filter(group => map.has(group) && map.get(group)!.length > 0);
  });

  // --- LIFECYCLE ---
  constructor() {
    // On a phone the pills scroll sideways: the one of the saved region must be in view.
    afterRenderEffect(() => {
      this.activeRegion();
      this.regionKeys();
      this.showPressedRegion();
    });
  }

  ngOnInit(): void {
    this.muscleService.refreshNeeded$.subscribe(() => {
      this.loadMuscles();
    });
    this.muscleService.getReferenceData().subscribe(ref => {
      this.regionKeys.set(ref.regions);
      this.loadMuscles();
    });
  }

  // --- DATA FETCHING ---
  loadMuscles(): void {
    this.isLoading.set(true);
    const context = new HttpContext().set(BYPASS_LOADER, true);
    this.muscleService.getMuscles(context).subscribe({
      next: (data) => {
        this.rawMuscles.set(data);
        this.isLoading.set(false);
      },
      error: () => {
        this.rawMuscles.set([]);
        this.isLoading.set(false);
      }
    });
  }

  // --- ACTIONS & EVENTS ---
  onSearchChange(query: string): void {
    this.searchQuery.set(query);
  }

  exportCsv(): void {
    this.exporterService.executeCsvExport(
      this.exporterService.exportMuscles(),
      this.isExporting
    );
  }

  // --- HELPERS ---
  /** Scrolls the row of pills only, never the page. */
  private showPressedRegion(): void {
    const row = this.regions()?.nativeElement;
    const pill = row?.querySelector<HTMLElement>('[aria-pressed="true"]');
    if (!row || !pill) return;
    const left = pill.offsetLeft - row.offsetLeft;
    if (left < row.scrollLeft) row.scrollLeft = left;
    else if (left + pill.offsetWidth > row.scrollLeft + row.clientWidth) row.scrollLeft = left + pill.offsetWidth - row.clientWidth;
  }

  /** The thumbnail of the 3D model when the muscle is in it, else the image set in the catalog. */
  imageOf(muscle: MuscleResponse): string | undefined {
    return muscleThumbUrl(muscle.code, this.themeService.currentTheme()) ?? muscle.imageUrl;
  }

  getLocalizedName(muscle: MuscleResponse): string {
    const isFr = this.activeLang() === 'fr';
    const name = isFr ? muscle.commonNameFr : muscle.commonNameEn;
    return name || muscle.medicalName;
  }
}
