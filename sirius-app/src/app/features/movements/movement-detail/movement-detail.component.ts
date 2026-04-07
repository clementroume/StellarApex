import {ChangeDetectionStrategy, Component, computed, effect, inject, input, signal} from '@angular/core';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {Router, RouterModule} from '@angular/router';
import {Location, NgOptimizedImage} from '@angular/common';
import {NgIconComponent} from '@ng-icons/core';
import {MovementResponse} from '../../../api/aldebaran/models/movement.model';
import {MuscleResponse} from '../../../api/aldebaran/models/muscle.model';
import {AuthService} from '../../../api/antares/services/auth.service';
import {MovementService} from '../../../api/aldebaran/services/movement.service';
import {NotificationService} from '../../../core/services/notification.service';
import {HttpErrorResponse} from '@angular/common/http';

@Component({
  selector: 'app-movement-detail',
  standalone: true,
  imports: [TranslateModule, RouterModule, NgIconComponent, NgOptimizedImage],
  templateUrl: './movement-detail.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MovementDetailComponent {
  // --- INJECTIONS ---
  id = input.required<string>();
  private readonly translate = inject(TranslateService);
  private readonly authService = inject(AuthService);
  private readonly movementService = inject(MovementService);
  private readonly notificationService = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly location = inject(Location);

  // --- STATE (SIGNALS) ---
  activeLang = signal<string>(this.translate.getCurrentLang() || this.translate.getFallbackLang() || 'en');
  isAdmin = computed(() => this.authService.currentUser()?.platformRole === 'ADMIN');

  movement = signal<MovementResponse | null>(null);
  isLoading = signal<boolean>(true); // Nouvel état explicite

  constructor() {
    this.translate.onLangChange.subscribe(event => this.activeLang.set(event.lang));

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

  getLocalizedMuscleName(muscle: MuscleResponse): string {
    const isFr = this.activeLang() === 'fr';
    return (isFr ? muscle.commonNameFr : muscle.commonNameEn) || muscle.medicalName;
  }
}
