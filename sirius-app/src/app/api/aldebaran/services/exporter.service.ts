import {inject, Injectable, WritableSignal} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../../environments/environment';
import {NotificationService} from '../../../core/services/notification.service';
import {TranslateService} from '@ngx-translate/core';

@Injectable({
  providedIn: 'root'
})
export class ExporterService {
  private readonly http = inject(HttpClient);

  constructor(
    private readonly translate: TranslateService,
    private readonly notificationService: NotificationService
  ) {
  }

  executeCsvExport<T>(exportRequest: Observable<T>, loadingSignal: WritableSignal<boolean>): void {
    if (!confirm(this.translate.instant('EXPORT.CONFIRM_EXPORT'))) {
      return;
    }

    loadingSignal.set(true);

    exportRequest.subscribe({
      next: () => {
        this.notificationService.showSuccess(this.translate.instant('EXPORT.EXPORT_SUCCESS'));
        loadingSignal.set(false);
      },
      error: () => {
        this.notificationService.showError(this.translate.instant('EXPORT.EXPORT_ERROR'));
        loadingSignal.set(false);
      }
    });
  }

  exportMuscles(): Observable<string> {
    return this.http.post((this.buildUrl(`/muscles`)), {}, {responseType: 'text' as const});
  }

  exportMovements(): Observable<string> {
    return this.http.post((this.buildUrl(`/movements`)), {}, {responseType: 'text' as const});
  }

  private buildUrl(path: string): string {
    return `${environment.trainingUrl}/export${path}`;
  }
}
