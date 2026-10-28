import {ChangeDetectionStrategy, Component, computed, inject, OnInit, signal} from '@angular/core';
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
import {ListItemComponent} from '../../../shared/component/list/list-item/list-item.component';
import {ListGridComponent} from '../../../shared/component/list/list-grid/list-grid.component';

/** A row of a region: a top-level muscle (group or standalone) or a member shown under its group. */
export interface MuscleRow {
  muscle: MuscleResponse;
  isMember: boolean;
}

import {ListGroupComponent} from '../../../shared/component/list/list-group/list-group.component';
import {HlmButton} from '@spartan-ng/helm/button';
@Component({
  selector: 'app-muscle-list',
  standalone: true,
  imports: [ListGroupComponent, HlmButton, RouterModule, TranslatePipe, NgIcon, SearchInputComponent, ListStateComponent, ListItemComponent, ListGridComponent],
  templateUrl: './muscle-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MuscleListComponent implements OnInit {
  // --- INJECTIONS ---
  private readonly muscleService = inject(MuscleService);
  private readonly accessService = inject(AccessService);
  private readonly translate = inject(TranslateService);
  private readonly exporterService = inject(ExporterService);

  // --- STATE (SIGNALS & VARIABLES) ---
  isLoading = signal<boolean>(false);
  isExporting = signal<boolean>(false);
  activeLang = computed(() => this.translate.currentLang() ?? this.translate.fallbackLang() ?? 'en');

  rawMuscles = signal<MuscleResponse[]>([]);
  muscleGroupKeys = signal<string[]>([]);

  // UI State persisted in service
  searchQuery = this.muscleService.savedSearchQuery;
  expandedGroups = this.muscleService.savedExpandedGroups;
  private preSearchState: Set<string> | null = null;

  // --- COMPUTED STATE ---
  isAdmin = this.accessService.isAdmin;

  filteredMuscles = computed(() => {
    const query = this.searchQuery().toLowerCase().trim();
    const list = this.rawMuscles();
    if (!query) return list;
    return list.filter(m =>
      m.medicalName.toLowerCase().includes(query) ||
      this.getLocalizedName(m).toLowerCase().includes(query)
    );
  });

  /**
   * Rows per region: each top-level muscle (functional group or standalone) followed by its members.
   * A group stays visible when only one of its members matches the search.
   */
  groupedMuscles = computed(() => {
    const all = this.rawMuscles();
    const matched = new Set(this.filteredMuscles().map(m => m.id));
    const ids = new Set(all.map(m => m.id));
    const byName = (a: MuscleResponse, b: MuscleResponse) => a.medicalName.localeCompare(b.medicalName);
    const membersOf = new Map<number, MuscleResponse[]>();
    all.filter(m => m.parent && ids.has(m.parent.id)).forEach(m => {
      membersOf.set(m.parent!.id, [...(membersOf.get(m.parent!.id) ?? []), m]);
    });

    const grouped = new Map<string, MuscleRow[]>();
    this.muscleGroupKeys().forEach(g => grouped.set(g, []));
    all.filter(m => !m.parent || !ids.has(m.parent.id)).sort(byName).forEach(top => {
      const members = (membersOf.get(top.id) ?? []).filter(m => matched.has(m.id)).sort(byName);
      if (!matched.has(top.id) && members.length === 0) return;
      if (!grouped.has(top.muscleGroup)) {
        grouped.set(top.muscleGroup, []);
      }
      grouped.get(top.muscleGroup)!.push(
        {muscle: top, isMember: false},
        ...members.map(muscle => ({muscle, isMember: true})));
    });
    return grouped;
  });

  populatedGroups = computed(() => {
    const groups = this.muscleGroupKeys();
    const map = this.groupedMuscles();
    return groups.filter(group => map.has(group) && map.get(group)!.length > 0);
  });

  // --- LIFECYCLE ---
  ngOnInit(): void {
    this.muscleService.refreshNeeded$.subscribe(() => {
      this.loadMuscles();
    });
    this.muscleService.getReferenceData().subscribe(ref => {
      this.muscleGroupKeys.set(ref.muscleGroups);
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
    const trimmedQuery = query.trim();
    const previousQuery = this.searchQuery().trim();

    if (previousQuery.length === 0 && trimmedQuery.length > 0) {
      this.preSearchState = new Set(this.expandedGroups());
    }

    this.searchQuery.set(query);

    if (trimmedQuery.length === 0) {
      if (this.preSearchState) {
        this.muscleService.savedExpandedGroups.set(new Set(this.preSearchState));
        this.preSearchState = null;
      }
    } else {
      const expanded = new Set(this.expandedGroups());
      for (const [group, rows] of this.groupedMuscles().entries()) {
        if (rows.length > 0) {
          expanded.add(group);
        }
      }
      this.muscleService.savedExpandedGroups.set(expanded);
    }
  }

  onGroupToggle(group: string, isExpanded: boolean): void {
    this.muscleService.toggleGroupExpansion(group, isExpanded);
  }

  exportCsv(): void {
    this.exporterService.executeCsvExport(
      this.exporterService.exportMuscles(),
      this.isExporting
    );
  }

  // --- HELPERS ---
  /** Badge of a functional group: "Group · 4". */
  groupBadge(muscle: MuscleResponse): string | undefined {
    const count = muscle.members?.length ?? 0;
    return count > 0 ? `${this.translate.instant('MUSCLE.MESSAGES.GROUP_BADGE')} · ${count}` : undefined;
  }

  getLocalizedName(muscle: MuscleResponse): string {
    const isFr = this.activeLang() === 'fr';
    const name = isFr ? muscle.commonNameFr : muscle.commonNameEn;
    return name || muscle.medicalName;
  }
}
