import {ChangeDetectionStrategy, Component, computed, inject, OnInit, signal} from '@angular/core';
import {RouterModule} from '@angular/router';
import {toSignal} from '@angular/core/rxjs-interop';
import {LangChangeEvent, TranslateModule, TranslateService} from '@ngx-translate/core';
import {MuscleService} from '../../../api/aldebaran/services/muscle.service';
import {MuscleResponse} from '../../../api/aldebaran/models/muscle.model';
import {AuthService} from '../../../api/antares/services/auth.service';
import {NgIcon} from '@ng-icons/core';
import {DialogService} from '../../../core/services/dialog.service';
import {startWith, switchMap} from 'rxjs';
import {ExporterService} from '../../../api/aldebaran/services/exporter.service';
import {NotificationService} from '../../../core/services/notification.service';

type SortState = { column: keyof MuscleResponse | 'commonName' | '', direction: 'asc' | 'desc' };

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
  readonly rawMuscles = toSignal(
    this.muscleService.refreshNeeded$.pipe(
      startWith(undefined),
      switchMap(() => this.muscleService.getMuscles())
    ),
    {initialValue: []}
  );

  activeLang = signal<string>(this.translate.getCurrentLang() || this.translate.getFallbackLang() || 'en');

  sortStates = signal<Record<string, SortState>>({});
  muscleGroupKeys = signal<string[]>([]);
  isExporting = signal<boolean>(false);

  constructor() {
    this.translate.onLangChange.subscribe((event: LangChangeEvent) => {
      this.activeLang.set(event.lang);
    });
  }

  ngOnInit(): void {
    this.muscleService.getReferenceData().subscribe(ref => {
      this.muscleGroupKeys.set(ref.muscleGroups);
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

  groupedMuscles = computed(() => {
    const muscles = [...this.rawMuscles()];
    const states = this.sortStates();

    // 1. Grouping by key
    const grouped = new Map<string, MuscleResponse[]>();
    this.muscleGroupKeys().forEach(g => grouped.set(g, []));

    muscles.forEach(m => {
      if (!grouped.has(m.muscleGroup)) {
        grouped.set(m.muscleGroup, []);
      }
      grouped.get(m.muscleGroup)!.push(m);
    });

    // 2. Independent sorting for each table
    grouped.forEach((list, group) => {
      const state = states[group];

      if (state?.column) {
        const direction = state.direction === 'asc' ? 1 : -1;

        list.sort((a, b) => {
          let valA: any;
          let valB: any;

          if (state.column === 'commonName') {
            valA = this.getLocalizedName(a).toLowerCase();
            valB = this.getLocalizedName(b).toLowerCase();
          } else {
            valA = a[state.column as keyof MuscleResponse] || '';
            valB = b[state.column as keyof MuscleResponse] || '';
          }

          if (valA > valB) return direction;
          if (valA < valB) return -direction;
          return 0;
        });
      }
    });

    return grouped;
  });

  // 3-state sorting logic (ASC -> DESC -> RESET)
  sortBy(group: string, column: keyof MuscleResponse | 'commonName') {
    this.sortStates.update(states => {
      const current = states[group];

      // 1st click (or new column) -> ASC
      if (current?.column !== column) {
        return {...states, [group]: {column, direction: 'asc'}};
      }

      // 2nd click -> DESC
      if (current.direction === 'asc') {
        return {...states, [group]: {column, direction: 'desc'}};
      }

      // 3rd click -> RESET (Remove sort state for this group)
      const newStates = {...states};
      delete newStates[group];
      return newStates;
    });
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
