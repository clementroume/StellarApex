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

  modalityKeys = signal<string[]>([]);
  private readonly categoryToModality = new Map<string, string>();

  groupedMovements = computed(() => {
    const grouped = new Map<string, MovementSummaryResponse[]>();

    this.modalityKeys().forEach(mod => grouped.set(mod, []));

    this.movements().forEach(m => {
      const mod = this.categoryToModality.get(m.category);
      if (mod && grouped.has(mod)) {
        grouped.get(mod)!.push(m);
      }
    });

    return grouped;
  });

  isExporting = signal<boolean>(false);

  ngOnInit(): void {
    this.movementService.refreshNeeded$.subscribe(() => {
      this.loadMovements();
    });

    this.movementService.getReferenceData().subscribe(ref => {

      const keys = Object.keys(ref.categoryGroups);
      this.modalityKeys.set(keys);

      Object.entries(ref.categoryGroups).forEach(([modality, categories]) => {
        categories.forEach(cat => this.categoryToModality.set(cat, modality));
      });

      this.loadMovements();
    });
  }

  loadMovements(query: string = ''): void {
    this.isLoading.set(true);
    this.movementService.searchMovements(query).subscribe({
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
