import {ChangeDetectionStrategy, Component, computed, inject, OnInit, signal} from '@angular/core';
import {RouterModule} from '@angular/router';
import {LangChangeEvent, TranslateModule, TranslateService} from '@ngx-translate/core';
import {MuscleService} from '../../../api/aldebaran/services/muscle.service';
import {MuscleResponse} from '../../../api/aldebaran/models/muscle.model';
import {AuthService} from '../../../api/antares/services/auth.service';
import {NgIcon} from '@ng-icons/core';
import {DialogService} from '../../../core/services/dialog.service';
import {ExporterService} from '../../../api/aldebaran/services/exporter.service';
import {NotificationService} from '../../../core/services/notification.service';
import {HttpContext} from '@angular/common/http';
import {BYPASS_LOADER} from '../../../core/interceptors/loading.interceptor';

@Component({
  selector: 'app-muscle-list',
  standalone: true,
  imports: [RouterModule, TranslateModule, NgIcon],
  templateUrl: './muscle-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MuscleListComponent implements OnInit {
  private readonly muscleService = inject(MuscleService);
  private readonly authService = inject(AuthService);
  private readonly translate = inject(TranslateService);
  private readonly dialogService = inject(DialogService);
  private readonly exporterService = inject(ExporterService);
  private readonly notificationService = inject(NotificationService);

  isAdmin = computed(() => this.authService.currentUser()?.platformRole === 'ADMIN');

  rawMuscles = signal<MuscleResponse[]>([]);
  isLoading = signal<boolean>(false);
  isExporting = signal<boolean>(false);

  activeLang = signal<string>(this.translate.getCurrentLang() || this.translate.getFallbackLang() || 'en');
  muscleGroupKeys = signal<string[]>([]);

  groupedMuscles = computed(() => {
    const grouped = new Map<string, MuscleResponse[]>();
    this.muscleGroupKeys().forEach(g => grouped.set(g, []));

    this.rawMuscles().forEach(m => {
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

  loadMuscles(): void {
    this.isLoading.set(true);
    const context = new HttpContext().set(BYPASS_LOADER, true);

    this.muscleService.getMuscles(context).subscribe({
      next: (data) => {
        this.rawMuscles.set(data);
        this.isLoading.set(false);
      },
      error: (err) => {
        console.error('Erreur lors du chargement des muscles', err);
        this.rawMuscles.set([]);
        this.isLoading.set(false);
      }
    });
  }

  getLocalizedName(muscle: MuscleResponse): string {
    const isFr = this.activeLang() === 'fr';
    const name = isFr ? muscle.commonNameFr : muscle.commonNameEn;
    return name || muscle.medicalName;
  }

  getLocalizedDescription(muscle: MuscleResponse): string {
    return this.activeLang() === 'fr' ? (muscle.descriptionFr || '') : (muscle.descriptionEn || '');
  }

  openDetails(id: number): void {
    this.muscleService.getMuscle(id).subscribe({
      next: (muscle) => {
        this.dialogService.openMuscle(muscle);
      },
      error: (err) => {
        console.error('Erreur lors du chargement des détails du muscle', err);
      }
    });
  }

  exportCsv(): void {
    if (!confirm(this.translate.instant('EXPORT.CONFIRM_EXPORT'))) {
      return;
    }

    this.isExporting.set(true);
    // noinspection DuplicatedCode
    this.exporterService.exportMuscles().subscribe({
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
