import {ChangeDetectionStrategy, Component, computed, inject, signal} from '@angular/core';
import {RouterModule} from '@angular/router';
import {toSignal} from '@angular/core/rxjs-interop';
import {LangChangeEvent, TranslateModule, TranslateService} from '@ngx-translate/core';
import {MuscleService} from '../../../api/aldebaran/services/muscle.service';
import {MuscleResponse} from '../../../api/aldebaran/models/muscle.model';
import {AuthService} from '../../../api/antares/services/auth.service';

@Component({
  selector: 'app-muscle-list',
  standalone: true,
  imports: [RouterModule, TranslateModule],
  templateUrl: './muscle-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MuscleListComponent {
  private readonly muscleService = inject(MuscleService);
  private readonly authService = inject(AuthService);
  private readonly translate = inject(TranslateService);

  isAdmin = computed(() => this.authService.currentUser()?.platformRole === 'ADMIN');
  private readonly rawMuscles = toSignal(this.muscleService.getMuscles(), {initialValue: []});

  // 1. Signal pour suivre la langue active de l'application
  activeLang = signal<string>(this.translate.getCurrentLang() || this.translate.getFallbackLang() || 'en');

  // État du tri (modifié pour utiliser un champ abstrait 'commonName')
  sortColumn = signal<keyof MuscleResponse | 'commonName' | ''>('');
  sortDirection = signal<'asc' | 'desc'>('asc');
  selectedMuscle = signal<MuscleResponse | null>(null);

  constructor() {
    // 2. Mettre à jour le signal si l'utilisateur change de langue depuis la navbar
    this.translate.onLangChange.subscribe((event: LangChangeEvent) => {
      this.activeLang.set(event.lang);
    });
  }

  // Helper : Récupère le nom en fonction de la langue
  getLocalizedName(muscle: MuscleResponse): string {
    const isFr = this.activeLang() === 'fr';
    const name = isFr ? muscle.commonNameFr : muscle.commonNameEn;
    return name || muscle.medicalName; // Fallback sur le nom médical si la trad est vide
  }

  // Helper : Récupère la description en fonction de la langue
  getLocalizedDescription(muscle: MuscleResponse): string {
    return this.activeLang() === 'fr' ? (muscle.descriptionFr || '') : (muscle.descriptionEn || '');
  }

  // 3. Mise à jour de la logique de tri pour gérer le nom dynamique
  sortedMuscles = computed(() => {
    const muscles = [...this.rawMuscles()];
    const column = this.sortColumn();
    const direction = this.sortDirection() === 'asc' ? 1 : -1;

    if (!column) return muscles;

    return muscles.sort((a, b) => {
      let valA: any = '';
      let valB: any = '';

      if (column === 'commonName') {
        valA = this.getLocalizedName(a).toLowerCase();
        valB = this.getLocalizedName(b).toLowerCase();
      } else {
        valA = a[column as keyof MuscleResponse] || '';
        valB = b[column as keyof MuscleResponse] || '';
      }

      return valA > valB ? direction : valA < valB ? -direction : 0;
    });
  });

  sortBy(column: keyof MuscleResponse | 'commonName') {
    if (this.sortColumn() === column) {
      this.sortDirection.set(this.sortDirection() === 'asc' ? 'desc' : 'asc');
    } else {
      this.sortColumn.set(column);
      this.sortDirection.set('asc');
    }
  }

  openDetails(muscle: MuscleResponse) {
    this.selectedMuscle.set(muscle);
    (document.getElementById('muscle_detail_modal') as HTMLDialogElement).showModal();
  }
}
