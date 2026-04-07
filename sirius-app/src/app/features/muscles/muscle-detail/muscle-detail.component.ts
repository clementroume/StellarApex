import {ChangeDetectionStrategy, Component, computed, effect, inject, input, signal} from '@angular/core';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {Router, RouterModule} from '@angular/router';
import {Location, NgOptimizedImage} from '@angular/common';
import {NgIconComponent} from '@ng-icons/core';
import {MuscleResponse} from '../../../api/aldebaran/models/muscle.model';
import {AuthService} from '../../../api/antares/services/auth.service';
import {MuscleService} from '../../../api/aldebaran/services/muscle.service';
import {NotificationService} from '../../../core/services/notification.service';
import {HttpErrorResponse} from '@angular/common/http';

@Component({
  selector: 'app-muscle-detail',
  standalone: true,
  imports: [TranslateModule, RouterModule, NgIconComponent, NgOptimizedImage],
  templateUrl: './muscle-detail.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MuscleDetailComponent {
  // --- INJECTIONS ---
  id = input.required<string>();
  private readonly translate = inject(TranslateService);
  private readonly authService = inject(AuthService);
  private readonly muscleService = inject(MuscleService);
  private readonly notificationService = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly location = inject(Location);

  // --- STATE (SIGNALS) ---
  activeLang = signal<string>(this.translate.getCurrentLang() || this.translate.getFallbackLang() || 'en');
  isAdmin = computed(() => this.authService.currentUser()?.platformRole === 'ADMIN');

  muscle = signal<MuscleResponse | null>(null);
  isLoading = signal<boolean>(true); // Nouvel état explicite

  constructor() {
    this.translate.onLangChange.subscribe(event => this.activeLang.set(event.lang));

    effect(() => {
      const currentId = Number(this.id());
      if (currentId && !Number.isNaN(currentId)) {
        this.loadMuscle(currentId);
      }
    });
  }

  // --- DATA FETCHING ---
  private loadMuscle(id: number): void {
    this.isLoading.set(true);

    this.muscleService.getMuscle(id).subscribe({
      next: (m) => {
        this.muscle.set(m);
        this.isLoading.set(false);
      },
      error: () => {
        this.notificationService.showError(this.translate.instant('MUSCLE.MESSAGES.NOT_FOUND'));
        this.isLoading.set(false);
        this.location.back();
      }
    });
  }

  // --- ACTIONS ---
  goBack(): void {
    this.location.back();
  }

  onDelete(muscle: MuscleResponse): void {
    const name = this.getLocalizedName(muscle);
    const confirmMsg = this.translate.instant('GLOBAL.CONFIRM_DELETE', {name});

    if (confirm(confirmMsg)) {
      this.muscleService.deleteMuscle(muscle.id).subscribe({
        next: () => {
          this.notificationService.showSuccess(this.translate.instant('GLOBAL.DELETE_SUCCESS'));
          this.muscleService.notifyRefresh();
          void this.router.navigate(['/muscles']);
        },
        error: (err: HttpErrorResponse) => {
          if (err.status === 409) {
            this.notificationService.showError(this.translate.instant('MUSCLE.MESSAGES.DELETE_CONFLICT', {name}));
          } else {
            this.notificationService.showError(this.translate.instant('GLOBAL.DELETE_ERROR'));
          }
        }
      });
    }
  }

  // --- HELPERS ---
  getLocalizedName(muscle: MuscleResponse): string {
    const isFr = this.activeLang() === 'fr';
    return (isFr ? muscle.commonNameFr : muscle.commonNameEn) || muscle.medicalName;
  }

  getLocalizedDescription(muscle: MuscleResponse): string {
    return this.activeLang() === 'fr' ? (muscle.descriptionFr || '') : (muscle.descriptionEn || '');
  }
}
