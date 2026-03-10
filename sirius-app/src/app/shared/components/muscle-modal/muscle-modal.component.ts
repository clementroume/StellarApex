import {Component, computed, effect, ElementRef, inject, signal, ViewChild} from '@angular/core';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {RouterModule} from '@angular/router';
import {DialogService} from '../../../core/services/dialog.service';
import {MuscleResponse} from '../../../api/aldebaran/models/muscle.model';
import {AuthService} from '../../../api/antares/services/auth.service';

@Component({
  selector: 'app-muscle-modal',
  standalone: true,
  imports: [TranslateModule, RouterModule],
  templateUrl: './muscle-modal.component.html'
})
export class MuscleModalComponent {
  dialogService = inject(DialogService);
  private readonly translate = inject(TranslateService);
  private readonly authService = inject(AuthService);

  @ViewChild('muscleModal') modalRef!: ElementRef<HTMLDialogElement>;

  activeLang = signal<string>(this.translate.getCurrentLang() || this.translate.getFallbackLang() || 'en');
  isAdmin = computed(() => this.authService.currentUser()?.platformRole === 'ADMIN');

  constructor() {
    this.translate.onLangChange.subscribe(event => this.activeLang.set(event.lang));

    effect(() => {
      const muscle = this.dialogService.muscleToView();
      if (muscle && this.modalRef?.nativeElement) {
        this.modalRef.nativeElement.showModal();
      } else if (!muscle && this.modalRef?.nativeElement) {
        this.modalRef.nativeElement.close();
      }
    });
  }

  getLocalizedName(muscle: MuscleResponse): string {
    const isFr = this.activeLang() === 'fr';
    return (isFr ? muscle.commonNameFr : muscle.commonNameEn) || muscle.medicalName;
  }

  getLocalizedDescription(muscle: MuscleResponse): string {
    return this.activeLang() === 'fr' ? (muscle.descriptionFr || '') : (muscle.descriptionEn || '');
  }

  onClose() {
    this.dialogService.closeMuscle();
  }
}
