import {ChangeDetectionStrategy, Component, computed, inject, OnInit, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {RouterModule} from '@angular/router';
import {TranslateModule} from '@ngx-translate/core';
import {FormsModule} from '@angular/forms';
import {MovementService} from '../../../api/aldebaran/services/movement.service';
import {AuthService} from '../../../api/antares/services/auth.service';
import {MovementSummaryResponse} from '../../../api/aldebaran/models/movement.model';
import {NgIcon} from '@ng-icons/core';
import {ExporterService} from '../../../api/aldebaran/services/exporter.service';
import {HttpContext} from '@angular/common/http';
import {BYPASS_LOADER} from '../../../core/interceptors/loading.interceptor';
import {SearchInputComponent} from '../../../shared/component/search-input/search-input.component';
import {ListStateComponent} from '../../../shared/component/list/list-state/list-state.component';
import {ListItemComponent} from '../../../shared/component/list/list-item/list-item.component';
import {ListGridComponent} from '../../../shared/component/list/list-grid/list-grid.component';

@Component({
  selector: 'app-movement-list',
  standalone: true,
  imports: [CommonModule, RouterModule, TranslateModule, FormsModule, NgIcon, SearchInputComponent, ListStateComponent, ListItemComponent, ListGridComponent],
  templateUrl: './movement-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MovementListComponent implements OnInit {
  // --- INJECTIONS ---
  private readonly movementService = inject(MovementService);
  private readonly authService = inject(AuthService);
  private readonly exporterService = inject(ExporterService);

  // --- STATE (SIGNALS & VARIABLES) ---
  isLoading = signal<boolean>(false);
  isExporting = signal<boolean>(false);

  rawMovements = signal<MovementSummaryResponse[]>([]);
  categoryGroups = signal<Record<string, string[]>>({});
  modalityKeys = signal<string[]>([]);

  // UI State persisted in service
  searchQuery = this.movementService.savedSearchQuery;
  activeTab = this.movementService.savedActiveTab;
  expandedCategories = this.movementService.savedExpandedCategories;
  private preSearchState: Set<string> | null = null;

  // --- COMPUTED STATE ---
  isAdmin = computed(() => this.authService.currentUser()?.platformRole === 'ADMIN');

  filteredMovements = computed(() => {
    const query = this.searchQuery().toLowerCase().trim();
    const list = this.rawMovements();
    if (!query) return list;
    return list.filter(m =>
      m.name.toLowerCase().includes(query) ||
      m.nameAbbreviation?.toLowerCase().includes(query)
    );
  });

  movementsByCategory = computed(() => {
    const grouped = new Map<string, MovementSummaryResponse[]>();
    Object.values(this.categoryGroups()).forEach(categories => {
      categories.forEach(category => grouped.set(category, []));
    });
    this.filteredMovements().forEach(m => {
      if (!grouped.has(m.category)) {
        grouped.set(m.category, []);
      }
      grouped.get(m.category)!.push(m);
    });
    grouped.forEach(list => {
      list.sort((a, b) => a.name.localeCompare(b.name));
    });
    return grouped;
  });

  populatedCategories = computed(() => {
    const activeTab = this.activeTab();
    const categoriesForTab = this.categoryGroups()[activeTab] || [];
    const map = this.movementsByCategory();
    return categoriesForTab.filter(category => map.has(category) && map.get(category)!.length > 0);
  });

  // --- LIFECYCLE ---
  ngOnInit(): void {
    this.movementService.refreshNeeded$.subscribe(() => {
      this.loadMovements();
    });
    this.movementService.getReferenceData().subscribe(ref => {
      this.categoryGroups.set(ref.categoryGroups);
      const keys = Object.keys(ref.categoryGroups);
      this.modalityKeys.set(keys);
      if (keys.length > 0 && !this.activeTab()) {
        this.activeTab.set(keys[0]);
      }
      this.loadMovements();
    });
  }

  // --- DATA FETCHING ---
  loadMovements(): void {
    this.isLoading.set(true);
    this.movementService.getMovements(new HttpContext().set(BYPASS_LOADER, true))
      .subscribe({
        next: (data) => {
          this.rawMovements.set(data);
          this.isLoading.set(false);
        },
        error: () => {
          this.rawMovements.set([]);
          this.isLoading.set(false);
        }
      });
  }

  // --- ACTIONS & EVENTS ---
  onSearchChange(query: string): void {
    const trimmedQuery = query.trim();
    const previousQuery = this.searchQuery().trim();

    if (previousQuery.length === 0 && trimmedQuery.length > 0) {
      this.preSearchState = new Set(this.expandedCategories());
    }

    this.searchQuery.set(query);

    if (trimmedQuery.length === 0) {
      if (this.preSearchState) {
        this.movementService.savedExpandedCategories.set(new Set(this.preSearchState));
        this.preSearchState = null;
      }
    } else {
      const expanded = new Set(this.expandedCategories());
      for (const [category, movements] of this.movementsByCategory().entries()) {
        if (movements.length > 0) {
          expanded.add(category);
        }
      }
      this.movementService.savedExpandedCategories.set(expanded);
    }
  }

  onCategoryToggle(category: string, event: Event): void {
    const isChecked = (event.target as HTMLInputElement).checked;
    this.movementService.toggleCategoryExpansion(category, isChecked);
  }

  exportCsv(): void {
    this.exporterService.executeCsvExport(
      this.exporterService.exportMovements(),
      this.isExporting
    );
  }

  // --- HELPERS ---
  getModalityIcon(modality: string): string {
    const normalized = modality.toUpperCase();
    switch (normalized) {
      case 'WEIGHTLIFTING':
        return 'hugeDumbbell01';
      case 'GYMNASTICS':
        return 'hugeGymnastic';
      case 'MONOSTRUCTURAL':
        return 'hugeCardiogram02';
      case 'STRONGMAN':
        return 'hugeBodyPartSixPack';
      default:
        return '';
    }
  }
}
