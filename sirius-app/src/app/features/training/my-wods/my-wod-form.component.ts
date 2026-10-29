import {ChangeDetectionStrategy, Component, effect, inject, input, signal, untracked} from '@angular/core';
import {HttpErrorResponse} from '@angular/common/http';
import {Router} from '@angular/router';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {HlmSpinner} from '@spartan-ng/helm/spinner';
import {WodService} from '../../../api/aldebaran/services/wod.service';
import {WodRequest, WodResponse} from '../../../api/aldebaran/models/wod.model';
import {AccessService} from '../../../core/services/access.service';
import {NotificationService} from '../../../core/services/notification.service';
import {problemDetailOf} from '../../../core/models/problem-detail.model';
import {PageComponent} from '../../../shared/component/page/page.component';
import {WodQuickFormComponent} from '../../wods/wod-quick-form/wod-quick-form.component';

/** Writes or edits a personal WOD with the quick editor; Aldebaran checks the author and the lock. */
@Component({
  selector: 'app-my-wod-form',
  standalone: true,
  imports: [TranslatePipe, HlmSpinner, PageComponent, WodQuickFormComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page [title]="(id() ? 'TRAINING.WODS.EDIT' : 'TRAINING.WODS.NEW') | translate" width="max-w-2xl">
      @if (isLoading()) {
        <div class="flex justify-center py-16"><hlm-spinner class="text-3xl text-primary" [aria-label]="'GLOBAL.LOADING' | translate"/></div>
      } @else {
        <app-wod-quick-form [initial]="wod()" (submitted)="save($event)" (cancelled)="cancel()"/>
      }
    </app-page>
  `
})
export class MyWodFormComponent {
  // --- INJECTIONS ---
  private readonly wodService = inject(WodService);
  private readonly accessService = inject(AccessService);
  private readonly notificationService = inject(NotificationService);
  private readonly translate = inject(TranslateService);
  private readonly router = inject(Router);

  // --- STATE (SIGNALS & VARIABLES) ---
  /** WOD to edit (route parameter), absent when creating. */
  readonly id = input<string>();
  protected readonly wod = signal<WodResponse | null>(null);
  protected readonly isLoading = signal(false);

  // --- LIFECYCLE ---
  constructor() {
    effect(() => {
      const id = Number(this.id());
      untracked(() => id && this.load(id));
    });
  }

  // --- DATA FETCHING ---
  private load(id: number): void {
    this.isLoading.set(true);
    this.wodService.getWod(id).subscribe({
      next: wod => {
        if (!wod.capabilities?.canModify || wod.capabilities.locked) {
          this.notificationService.showError(this.translate.instant(wod.capabilities?.locked ? 'WOD.MESSAGES.LOCKED' : 'WOD.MESSAGES.FORBIDDEN'));
          void this.router.navigate(['/wods', id]);
          return;
        }
        this.wod.set(wod);
        this.isLoading.set(false);
      },
      error: () => void this.router.navigate(['/training/wods'])
    });
  }

  // --- ACTIONS & EVENTS ---
  protected save(request: WodRequest): void {
    const personal: WodRequest = {...request, gymId: undefined, authorId: this.accessService.userId() ?? undefined};
    const current = this.wod();
    const call = current ? this.wodService.updateWod(current.id, personal) : this.wodService.createWod(personal);
    call.subscribe({
      next: saved => {
        this.notificationService.showSuccess(this.translate.instant(current ? 'WOD.MESSAGES.SUCCESS_UPDATE' : 'WOD.MESSAGES.SUCCESS_CREATE'));
        this.wodService.notifyRefresh();
        void this.router.navigate(['/wods', saved.id]);
      },
      error: (err: HttpErrorResponse) =>
        this.notificationService.showError(problemDetailOf(err) ?? this.translate.instant('GLOBAL.ERROR_UNEXPECTED'))
    });
  }

  protected cancel(): void {
    const current = this.wod();
    void this.router.navigate(current ? ['/wods', current.id] : ['/training/wods']);
  }
}
