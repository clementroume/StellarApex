import {ChangeDetectionStrategy, Component, computed, inject, OnInit, signal} from '@angular/core';
import {RouterModule} from '@angular/router';
import {LangChangeEvent, TranslateModule, TranslateService} from '@ngx-translate/core';
import {MuscleService} from '../../../api/aldebaran/services/muscle.service';
import {MuscleResponse} from '../../../api/aldebaran/models/muscle.model';
import {AuthService} from '../../../api/antares/services/auth.service';
import {NgIcon} from '@ng-icons/core';
import {ExporterService} from '../../../api/aldebaran/services/exporter.service';
import {HttpContext} from '@angular/common/http';
import {BYPASS_LOADER} from '../../../core/interceptors/loading.interceptor';
import {SearchInputComponent} from '../../../shared/component/search-input/search-input.component';
import {ListStateComponent} from '../../../shared/component/list/list-state/list-state.component';
import {ListItemComponent} from '../../../shared/component/list/list-item/list-item.component';
import {ListGridComponent} from '../../../shared/component/list/list-grid/list-grid.component';

@Component({
  selector: 'app-muscle-list',
  standalone: true,
  imports: [RouterModule, TranslateModule, NgIcon, SearchInputComponent, ListStateComponent, ListItemComponent, ListGridComponent],
  templateUrl: './muscle-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MuscleListComponent implements OnInit {
  // --- INJECTIONS ---
  private readonly muscleService = inject(MuscleService);
  private readonly authService = inject(AuthService);
  private readonly translate = inject(TranslateService);
  private readonly exporterService = inject(ExporterService);

  // --- STATE (SIGNALS & VARIABLES) ---
  isLoading = signal<boolean>(false);
  isExporting = signal<boolean>(false);
  activeLang = signal<string>(this.translate.getCurrentLang() || this.translate.getFallbackLang() || 'en');

  rawMuscles = signal<MuscleResponse[]>([]);
  muscleGroupKeys = signal<string[]>([]);

  // UI State persisted in service
  searchQuery = this.muscleService.savedSearchQuery;
  expandedGroups = this.muscleService.savedExpandedGroups;
  private preSearchState: Set<string> | null = null;

  // --- COMPUTED STATE ---
  isAdmin = computed(() => this.authService.currentUser()?.platformRole === 'ADMIN');

  filteredMuscles = computed(() => {
    const query = this.searchQuery().toLowerCase().trim();
    const list = this.rawMuscles();
    if (!query) return list;
    return list.filter(m =>
      m.medicalName.toLowerCase().includes(query) ||
      this.getLocalizedName(m).toLowerCase().includes(query)
    );
  });

  groupedMuscles = computed(() => {
    const grouped = new Map<string, MuscleResponse[]>();
    this.muscleGroupKeys().forEach(g => grouped.set(g, []));
    this.filteredMuscles().forEach(m => {
      if (!grouped.has(m.muscleGroup)) {
        grouped.set(m.muscleGroup, []);
      }
      grouped.get(m.muscleGroup)!.push(m);
    });
    grouped.forEach((list) => {
      list.sort((a, b) => a.medicalName.localeCompare(b.medicalName));
    });
    return grouped;
  });

  populatedGroups = computed(() => {
    const groups = this.muscleGroupKeys();
    const map = this.groupedMuscles();
    return groups.filter(group => map.has(group) && map.get(group)!.length > 0);
  });

  // --- LIFECYCLE ---
  constructor() {
    this.translate.onLangChange.subscribe((event: LangChangeEvent) => {
      this.activeLang.set(event.lang);
    });
  }

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
      for (const [group, muscles] of this.groupedMuscles().entries()) {
        if (muscles.length > 0) {
          expanded.add(group);
        }
      }
      this.muscleService.savedExpandedGroups.set(expanded);
    }
  }

  onGroupToggle(group: string, event: Event): void {
    const isChecked = (event.target as HTMLInputElement).checked;
    this.muscleService.toggleGroupExpansion(group, isChecked);
  }

  exportCsv(): void {
    this.exporterService.executeCsvExport(
      this.exporterService.exportMuscles(),
      this.isExporting
    );
  }

  // --- HELPERS ---
  getLocalizedName(muscle: MuscleResponse): string {
    const isFr = this.activeLang() === 'fr';
    const name = isFr ? muscle.commonNameFr : muscle.commonNameEn;
    return name || muscle.medicalName;
  }
}
