import {DOCUMENT, inject, Injectable} from '@angular/core';
import {SwUpdate, VersionReadyEvent} from '@angular/service-worker';
import {TranslateService} from '@ngx-translate/core';
import {filter} from 'rxjs';
import {NotificationService} from './notification.service';

/** Offers to reload when the service worker has downloaded a new version of the app. */
@Injectable({providedIn: 'root'})
export class AppUpdateService {
  // --- INJECTIONS ---
  private readonly swUpdate = inject(SwUpdate);
  private readonly notificationService = inject(NotificationService);
  private readonly translate = inject(TranslateService);
  private readonly document = inject(DOCUMENT);

  // --- ACTIONS & EVENTS ---
  watch(): void {
    if (!this.swUpdate.isEnabled) return;
    this.swUpdate.versionUpdates
      .pipe(filter((e): e is VersionReadyEvent => e.type === 'VERSION_READY'))
      .subscribe(() => this.notificationService.showAction(
        this.translate.instant('GLOBAL.UPDATE_READY'),
        this.translate.instant('GLOBAL.RELOAD'),
        () => this.document.location.reload()
      ));
  }
}
