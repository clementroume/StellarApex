import {ChangeDetectionStrategy, Component, computed, effect, inject, input, signal} from '@angular/core';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {Router, RouterModule} from '@angular/router';
import {Location, NgOptimizedImage} from '@angular/common';
import {NgIconComponent} from '@ng-icons/core';
import {MuscleRefResponse, MuscleResponse, MuscleUsage} from '../../../api/aldebaran/models/muscle.model';
import {AccessService} from '../../../core/services/access.service';
import {MuscleService} from '../../../api/aldebaran/services/muscle.service';
import {NotificationService} from '../../../core/services/notification.service';
import {HttpErrorResponse} from '@angular/common/http';
import {
  DetailAdminActionsComponent
} from '../../../shared/component/detail/detail-admin-actions/detail-admin-actions.component';
import {DetailCardComponent} from '../../../shared/component/detail/detail-card/detail-card.component';
import {DetailHeaderComponent} from '../../../shared/component/detail/detail-header/detail-header.component';
import {DetailStateComponent} from '../../../shared/component/detail/detail-state/detail-state.component';

import {HlmBadge} from '@spartan-ng/helm/badge';
import {HlmButton} from '@spartan-ng/helm/button';
@Component({
  selector: 'app-muscle-detail',
  standalone: true,
  imports: [HlmBadge, HlmButton, TranslatePipe, RouterModule, NgIconComponent, NgOptimizedImage, DetailAdminActionsComponent, DetailCardComponent, DetailHeaderComponent, DetailStateComponent],
  templateUrl: './muscle-detail.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MuscleDetailComponent {
  // --- INJECTIONS ---
  id = input.required<string>();
  private readonly translate = inject(TranslateService);
  private readonly accessService = inject(AccessService);
  private readonly muscleService = inject(MuscleService);
  private readonly notificationService = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly location = inject(Location);

  // --- STATE (SIGNALS) ---
  activeLang = computed(() => this.translate.currentLang() ?? this.translate.fallbackLang() ?? 'en');
  isAdmin = this.accessService.isAdmin;

  muscle = signal<MuscleResponse | null>(null);
  usage = signal<MuscleUsage[]>([]);
  readonly roles = ['AGONIST', 'SYNERGIST', 'STABILIZER'] as const;
  role = signal<MuscleUsage['role']>('AGONIST');
  readonly shownUsage = computed(() => this.usage().filter(u => u.role === this.role()));
  isLoading = signal<boolean>(true); // Nouvel état explicite

  constructor() {
    effect(() => {
      const currentId = Number(this.id());
      if (currentId && !Number.isNaN(currentId)) {
        this.loadMuscle(currentId);
        this.muscleService.getUsage(currentId).subscribe({
          next: usage => this.usage.set(usage),
          error: () => this.usage.set([])
        });
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

  countOf(role: MuscleUsage['role']): number {
    return this.usage().filter(u => u.role === role).length;
  }

  /** The technique or equipment that brings a role, translated. */
  triggerLabel(trigger: string): string {
    const technique = 'MOVEMENT.TECHNIQUES.' + trigger;
    const label = this.translate.instant(technique);
    return label === technique ? this.translate.instant('MOVEMENT.EQUIPMENT.' + trigger) : label;
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
  getLocalizedName(muscle: MuscleResponse | MuscleRefResponse): string {
    const isFr = this.activeLang() === 'fr';
    return (isFr ? muscle.commonNameFr : muscle.commonNameEn) || muscle.medicalName;
  }

  getLocalizedDescription(muscle: MuscleResponse): string {
    return this.activeLang() === 'fr' ? (muscle.descriptionFr || '') : (muscle.descriptionEn || '');
  }
}
