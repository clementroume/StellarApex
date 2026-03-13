import {Component, computed, effect, ElementRef, inject, signal, ViewChild} from '@angular/core';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {RouterModule} from '@angular/router';
import {DialogService} from '../../../core/services/dialog.service';
import {MovementResponse} from '../../../api/aldebaran/models/movement.model';
import {MuscleResponse} from '../../../api/aldebaran/models/muscle.model';
import {AuthService} from '../../../api/antares/services/auth.service';
import {NgIconComponent} from '@ng-icons/core';
import {MuscleService} from '../../../api/aldebaran/services/muscle.service';
import {NotificationService} from '../../../core/services/notification.service';
import {MovementService} from '../../../api/aldebaran/services/movement.service';
import {NgOptimizedImage} from '@angular/common';

@Component({
  selector: 'app-movement-modal',
  standalone: true,
  imports: [TranslateModule, RouterModule, NgIconComponent, NgOptimizedImage],
  templateUrl: './movement-modal.component.html'
})
export class MovementModalComponent {
  dialogService = inject(DialogService);
  private readonly translate = inject(TranslateService);
  private readonly authService = inject(AuthService);
  private readonly muscleService = inject(MuscleService);
  private readonly movementService = inject(MovementService);
  private readonly notificationService = inject(NotificationService);

  @ViewChild('movementModal') modalRef!: ElementRef<HTMLDialogElement>;

  activeLang = signal<string>(this.translate.getCurrentLang() || this.translate.getFallbackLang() || 'en');
  isAdmin = computed(() => this.authService.currentUser()?.platformRole === 'ADMIN');

  constructor() {
    this.translate.onLangChange.subscribe(event => this.activeLang.set(event.lang));

    effect(() => {
      const movement = this.dialogService.movementToView();
      if (movement && this.modalRef?.nativeElement) {
        this.modalRef.nativeElement.showModal();
      } else if (!movement && this.modalRef?.nativeElement) {
        this.modalRef.nativeElement.close();
      }
    });
  }

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

  onClose() {
    this.dialogService.closeMovement();
  }

  onDelete(): void {
    const movement = this.dialogService.movementToView();
    if (!movement) return;

    const confirmMsg = this.translate.instant('GLOBAL.CONFIRM_DELETE', {name: movement.name});

    if (confirm(confirmMsg)) {
      this.movementService.deleteMovement(movement.id).subscribe({
        next: () => {
          this.notificationService.showSuccess(this.translate.instant('GLOBAL.DELETE_SUCCESS'));
          this.movementService.notifyRefresh();
          this.onClose();
        },
        error: (err) => {
          if (err.status === 409) {
            this.notificationService.showError(this.translate.instant('MOVEMENT.MESSAGES.DELETE_CONFLICT', {name: movement.name}));
          } else {
            this.notificationService.showError(this.translate.instant('GLOBAL.DELETE_ERROR'));
          }
        }
      });
    }
  }

  openMuscle(id: number, event: Event): void {
    event.preventDefault();
    event.stopPropagation();

    this.muscleService.getMuscle(id).subscribe({
      next: (muscle) => {
        this.dialogService.openMuscle(muscle);
      },
      error: (err) => {
        console.error('Erreur lors du chargement des détails du muscle', err);
      }
    });
  }
}
