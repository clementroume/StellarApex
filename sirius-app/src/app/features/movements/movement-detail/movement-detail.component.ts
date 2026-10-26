import {ChangeDetectionStrategy, Component, computed, effect, inject, input, signal} from '@angular/core';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {Router, RouterModule} from '@angular/router';
import {Location, NgOptimizedImage, NgTemplateOutlet} from '@angular/common';
import {NgIconComponent} from '@ng-icons/core';
import {MovementResponse} from '../../../api/aldebaran/models/movement.model';
import {MuscleRefResponse, MuscleResponse} from '../../../api/aldebaran/models/muscle.model';
import {AccessService} from '../../../core/services/access.service';
import {MovementService} from '../../../api/aldebaran/services/movement.service';
import {NotificationService} from '../../../core/services/notification.service';
import {HttpErrorResponse} from '@angular/common/http';
import {
  DetailAdminActionsComponent
} from '../../../shared/component/detail/detail-admin-actions/detail-admin-actions.component';
import {DetailCardComponent} from '../../../shared/component/detail/detail-card/detail-card.component';
import {DetailHeaderComponent} from '../../../shared/component/detail/detail-header/detail-header.component';
import {DetailStateComponent} from '../../../shared/component/detail/detail-state/detail-state.component';

@Component({
  selector: 'app-movement-detail',
  standalone: true,
  imports: [TranslatePipe, RouterModule, NgIconComponent, NgOptimizedImage, NgTemplateOutlet, DetailAdminActionsComponent, DetailCardComponent, DetailHeaderComponent, DetailStateComponent],
  templateUrl: './movement-detail.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MovementDetailComponent {
  // --- INJECTIONS ---
  id = input.required<string>();
  private readonly translate = inject(TranslateService);
  private readonly accessService = inject(AccessService);
  private readonly movementService = inject(MovementService);
  private readonly notificationService = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly location = inject(Location);

  // --- STATE (SIGNALS) ---
  activeLang = computed(() => this.translate.currentLang() ?? this.translate.fallbackLang() ?? 'en');
  isAdmin = this.accessService.isAdmin;

  movement = signal<MovementResponse | null>(null);
  isLoading = signal<boolean>(true); // Nouvel état explicite

  constructor() {
    effect(() => {
      const currentId = Number(this.id());
      if (currentId && !Number.isNaN(currentId)) {
        this.loadMovement(currentId);
      }
    });
  }

  // --- DATA FETCHING ---
  private loadMovement(id: number): void {
    this.isLoading.set(true);

    this.movementService.getMovement(id).subscribe({
      next: (m) => {
        this.movement.set(m);
        this.isLoading.set(false);
      },
      error: () => {
        this.notificationService.showError(this.translate.instant('MOVEMENT.MESSAGES.NOT_FOUND'));
        this.isLoading.set(false);
        this.location.back();
      }
    });
  }

  // --- ACTIONS ---
  goBack(): void {
    this.location.back();
  }

  onDelete(movement: MovementResponse): void {
    const confirmMsg = this.translate.instant('GLOBAL.CONFIRM_DELETE', {name: movement.name});

    if (confirm(confirmMsg)) {
      this.movementService.deleteMovement(movement.id).subscribe({
        next: () => {
          this.notificationService.showSuccess(this.translate.instant('GLOBAL.DELETE_SUCCESS'));
          this.movementService.notifyRefresh();
          void this.router.navigate(['/movements']);
        },
        error: (err: HttpErrorResponse) => {
          if (err.status === 409) {
            this.notificationService.showError(this.translate.instant('MOVEMENT.MESSAGES.DELETE_CONFLICT', {name: movement.name}));
          } else {
            this.notificationService.showError(this.translate.instant('GLOBAL.DELETE_ERROR'));
          }
        }
      });
    }
  }

  // --- HELPERS ---
  getLocalizedDescription(movement: MovementResponse): string {
    return this.activeLang() === 'fr' ? (movement.descriptionFr || '') : (movement.descriptionEn || '');
  }

  getLocalizedCues(movement: MovementResponse): string {
    return this.activeLang() === 'fr' ? (movement.coachingCuesFr || '') : (movement.coachingCuesEn || '');
  }

  getLocalizedMuscleName(muscle: MuscleResponse | MuscleRefResponse): string {
    const isFr = this.activeLang() === 'fr';
    return (isFr ? muscle.commonNameFr : muscle.commonNameEn) || muscle.medicalName;
  }

  /** Tooltip of a targeted muscle: the muscles of a functional group, or the group of a member. */
  getMuscleHint(muscle: MuscleResponse): string | null {
    if (muscle.members?.length) {
      return `${this.translate.instant('MUSCLE.FIELDS.MEMBERS')} : ${muscle.members.map(m => this.getLocalizedMuscleName(m)).join(', ')}`;
    }
    if (muscle.parent) {
      return `${this.translate.instant('MUSCLE.FIELDS.FUNCTIONAL_GROUP')} : ${this.getLocalizedMuscleName(muscle.parent)}`;
    }
    return null;
  }
}
