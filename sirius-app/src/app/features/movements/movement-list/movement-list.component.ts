import {ChangeDetectionStrategy, Component, computed, inject, OnInit, signal} from '@angular/core';
import {CommonModule, NgOptimizedImage} from '@angular/common';
import {RouterModule} from '@angular/router';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {FormsModule} from '@angular/forms';
import {MovementService} from '../../../api/aldebaran/services/movement.service';
import {AuthService} from '../../../api/antares/services/auth.service';
import {MovementSummaryResponse} from '../../../api/aldebaran/models/movement.model';
import {NgIcon} from '@ng-icons/core';
import {DialogService} from '../../../core/services/dialog.service';
import {NotificationService} from '../../../core/services/notification.service';
import {ExporterService} from '../../../api/aldebaran/services/exporter.service';
import {HttpContext} from '@angular/common/http';
import {BYPASS_LOADER} from '../../../core/interceptors/loading.interceptor';

@Component({
  selector: 'app-movement-list',
  standalone: true,
  imports: [CommonModule, RouterModule, TranslateModule, FormsModule, NgIcon, NgOptimizedImage],
  templateUrl: './movement-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MovementListComponent implements OnInit {
  private readonly movementService = inject(MovementService);
  private readonly authService = inject(AuthService);
  private readonly dialogService = inject(DialogService);
  private readonly translate = inject(TranslateService);
  private readonly exporterService = inject(ExporterService);
  private readonly notificationService = inject(NotificationService);

  isAdmin = computed(() => this.authService.currentUser()?.platformRole === 'ADMIN');

  movements = signal<MovementSummaryResponse[]>([]);
  isLoading = signal<boolean>(false);
  isExporting = signal<boolean>(false);

  // Gestion des onglets et catégories
  categoryGroups = signal<Record<string, string[]>>({});
  modalityKeys = signal<string[]>([]);
  activeTab = signal<string>('');

  movementsByCategory = computed(() => {
    const grouped = new Map<string, MovementSummaryResponse[]>();
    this.movements().forEach(m => {
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

  ngOnInit(): void {
    this.movementService.refreshNeeded$.subscribe(() => {
      this.loadMovements();
    });

    this.movementService.getReferenceData().subscribe(ref => {
      this.categoryGroups.set(ref.categoryGroups);
      const keys = Object.keys(ref.categoryGroups);
      this.modalityKeys.set(keys);

      // Sélectionne le premier onglet par défaut
      if (keys.length > 0) {
        this.activeTab.set(keys[0]);
      }

      this.loadMovements();
    });
  }

  loadMovements(query: string = ''): void {
    this.isLoading.set(true);
    const context = new HttpContext().set(BYPASS_LOADER, true);

    this.movementService.searchMovements(query, context).subscribe({
      next: (data) => {
        this.movements.set(data);
        this.isLoading.set(false);
      },
      error: () => {
        this.movements.set([]);
        this.isLoading.set(false);
      }
    });
  }

  onSearchChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.loadMovements(input.value);
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

  openDetails(id: number): void {
    this.movementService.getMovement(id).subscribe({
      next: (movement) => {
        this.dialogService.openMovement(movement);
      },
      error: (err) => {
        console.error('Erreur lors du chargement des détails du mouvement', err);
      }
    });
  }

  exportCsv(): void {
    if (!confirm(this.translate.instant('EXPORT.CONFIRM_EXPORT'))) {
      return;
    }

    this.isExporting.set(true);
    // noinspection DuplicatedCode
    this.exporterService.exportMovements().subscribe({
      next: () => {
        this.notificationService.showSuccess(this.translate.instant('EXPORT.EXPORT_SUCCESS'));
        this.isExporting.set(false);
      },
      error: (err) => {
        console.error(err);
        this.notificationService.showError(this.translate.instant('EXPORT.EXPORT_ERROR'));
        this.isExporting.set(false);
      }
    });
  }
}
