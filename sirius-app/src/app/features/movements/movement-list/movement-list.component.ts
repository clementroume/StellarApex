import {ChangeDetectionStrategy, Component, computed, inject, OnInit, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {RouterModule} from '@angular/router';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {FormsModule} from '@angular/forms';
import {MovementService} from '../../../api/aldebaran/services/movement.service';
import {AccessService} from '../../../core/services/access.service';
import {MovementSummaryResponse, UsualNameOption} from '../../../api/aldebaran/models/movement.model';
import {NgIcon} from '@ng-icons/core';
import {ExporterService} from '../../../api/aldebaran/services/exporter.service';
import {HttpContext} from '@angular/common/http';
import {BYPASS_LOADER} from '../../../core/interceptors/loading.interceptor';
import {SearchInputComponent} from '../../../shared/component/search-input/search-input.component';
import {ListStateComponent} from '../../../shared/component/list/list-state/list-state.component';
import {normalize} from '../../../shared/text.utils';
import {HlmButton} from '@spartan-ng/helm/button';
import {HlmBadge} from '@spartan-ng/helm/badge';
import {RecordService} from '../../../api/aldebaran/services/record.service';
import {MovementRecords} from '../../../api/aldebaran/models/record.model';
import {UnitService} from '../../../core/services/unit.service';
import {LiftSummary, liftSummary} from '../../records/record.utils';

/** A card of the catalog. */
export interface MovementCard {
  movement: MovementSummaryResponse;
  variantCount: number;
  /** The first simple variants, without the movement's own name: "Back, Front, Goblet". */
  preview: string;
  record: LiftSummary | null;
}

const PREVIEW_SIZE = 4;


@Component({
  selector: 'app-movement-list',
  standalone: true,
  imports: [HlmButton, HlmBadge, CommonModule, RouterModule, TranslatePipe, FormsModule, NgIcon, SearchInputComponent, ListStateComponent],
  templateUrl: './movement-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    .cards { display: grid; grid-template-columns: minmax(0, 1fr); gap: 0.75rem; }
    .card { display: flex; gap: 0.75rem; align-items: center; height: 100%; padding: 0.75rem; }
    .thumb { display: flex; flex: none; align-items: center; justify-content: center; width: 3.5rem; height: 3.5rem; overflow: hidden; }
    .body { display: flex; flex: 1; flex-direction: column; gap: 0.25rem; min-width: 0; }
    .foot { display: flex; align-items: center; justify-content: space-between; gap: 0.5rem; margin-top: auto; }
    @media (min-width: 640px) { .cards { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
    @media (min-width: 768px) {
      .cards { grid-template-columns: repeat(3, minmax(0, 1fr)); }
      .card { flex-direction: column; align-items: stretch; }
      .thumb { width: 100%; height: 7rem; }
    }
  `
})
export class MovementListComponent implements OnInit {
  // --- INJECTIONS ---
  private readonly movementService = inject(MovementService);
  private readonly accessService = inject(AccessService);
  private readonly translate = inject(TranslateService);
  private readonly exporterService = inject(ExporterService);
  private readonly recordService = inject(RecordService);
  private readonly unitService = inject(UnitService);

  // --- STATE (SIGNALS & VARIABLES) ---
  isLoading = signal<boolean>(false);
  isExporting = signal<boolean>(false);

  rawMovements = signal<MovementSummaryResponse[]>([]);
  usualNames = signal<UsualNameOption[]>([]);
  categoryGroups = signal<Record<string, string[]>>({});
  modalityKeys = signal<string[]>([]);

  // UI State persisted in service
  searchQuery = this.movementService.savedSearchQuery;
  activeTab = this.movementService.savedActiveTab;
  activeFamily = this.movementService.savedFamily;
  private readonly records = signal<MovementRecords[]>([]);
  protected readonly lang = computed(() => this.translate.currentLang());

  // --- COMPUTED STATE ---
  isAdmin = this.accessService.isAdmin;

  /** Usual names, French terms included, that the search finds. */
  matchingNames = computed(() => {
    const query = normalize(this.searchQuery());
    if (!query) return [];
    return this.usualNames().filter(o => [o.nameEn, o.nameFr, o.searchTerms].some(t => normalize(t).includes(query)));
  });

  filteredMovements = computed(() => {
    const query = normalize(this.searchQuery());
    const list = this.rawMovements();
    if (!query) return list;
    const found = new Set(this.matchingNames().map(o => o.movementId));
    return list.filter(m => found.has(m.id) || normalize(m.name).includes(query) || normalize(m.nameAbbreviation).includes(query));
  });

  readonly isSearching = computed(() => normalize(this.searchQuery()) !== '');

  /** Discipline of each family, for the card without an image. */
  private readonly modalityOf = computed(() => new Map(
    Object.entries(this.categoryGroups()).flatMap(([modality, categories]) => categories.map(c => [c, modality] as const))));

  private readonly namesByMovement = computed(() => {
    const names = new Map<number, UsualNameOption[]>();
    this.usualNames().filter(o => o.nameEn !== o.movementName)
      .forEach(o => names.set(o.movementId, [...(names.get(o.movementId) ?? []), o]));
    return names;
  });

  /** Best tile of each movement: its base version, else its heaviest variant (a squat is always in a position). */
  private readonly recordByMovement = computed(() => new Map(this.records().map(r => {
    const base = r.variants.find(v => v.base);
    const summaries = (base ? [base] : r.variants).map(v => liftSummary(v)).filter((s): s is LiftSummary => s !== null);
    return [r.movementId, summaries.sort((a, b) => b.kg - a.kg)[0] ?? null] as const;
  })));

  /** Names to show above the families: those that are not just the movement's own name. */
  shownNames = computed(() => this.matchingNames().filter(o => o.nameEn !== o.movementName).slice(0, 12));

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

  /** Families of the discipline, with their number of movements. */
  families = computed(() => this.populatedCategories().map(category => ({
    category, count: this.movementsByCategory().get(category)?.length ?? 0
  })));

  /** The cards shown: the whole catalog while searching, else the family picked in the discipline. */
  cards = computed<MovementCard[]>(() => {
    const family = this.activeFamily();
    const shown = this.isSearching() ? this.filteredMovements()
      : this.populatedCategories().filter(c => family === null || c === family).flatMap(c => this.movementsByCategory().get(c) ?? []);
    return [...shown].sort((a, b) => a.name.localeCompare(b.name)).map(movement => {
      const names = this.namesByMovement().get(movement.id) ?? [];
      return {movement, variantCount: names.length, preview: this.previewOf(movement, names), record: this.recordByMovement().get(movement.id) ?? null};
    });
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
    this.movementService.getUsualNames(new HttpContext().set(BYPASS_LOADER, true)).subscribe({
      next: names => this.usualNames.set(names),
      error: () => this.usualNames.set([])
    });
    this.recordService.getMyRecords(new HttpContext().set(BYPASS_LOADER, true)).subscribe({
      next: records => this.records.set(records),
      error: () => this.records.set([])
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
    this.searchQuery.set(query);
  }

  setModality(modality: string): void {
    this.activeTab.set(modality);
    this.activeFamily.set(null);
  }

  exportCsv(): void {
    this.exporterService.executeCsvExport(
      this.exporterService.exportMovements(),
      this.isExporting
    );
  }

  // --- HELPERS ---
  /** Query parameters preselecting what a usual name stands for on the movement page. */
  queryOf(option: UsualNameOption): Record<string, string> {
    return {techniques: option.techniques.join(','), equipment: option.equipment.join(',')};
  }

  iconOf(movement: MovementSummaryResponse): string {
    return this.getModalityIcon(this.modalityOf().get(movement.category) ?? '');
  }

  /** "1RM 90 kg", "≈ 1RM 72 kg" for an estimate. */
  recordLoad(summary: LiftSummary): string {
    return summary.estimated ? this.unitService.formatPercentLoad(summary.kg) : this.unitService.formatScoreLoad(summary.kg, 'KG');
  }

  private previewOf(movement: MovementSummaryResponse, names: UsualNameOption[]): string {
    const own = new RegExp(`\\s*\\b${movement.name.replace(/[.*+?^\${}()|[\]\\]/g, '\\$&')}\\b\\s*`, 'i');
    const simple = names
      .filter(o => o.techniques.length + o.equipment.filter(e => e !== o.impliedEquipment).length === 1)
      .map(o => o.nameEn.replace(own, ' ').trim())
      .filter(Boolean)
      .sort((a, b) => a.localeCompare(b));
    return simple.slice(0, PREVIEW_SIZE).join(', ') + (simple.length > PREVIEW_SIZE ? '…' : '');
  }

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
