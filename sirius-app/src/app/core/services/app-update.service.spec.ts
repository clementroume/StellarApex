import {TestBed} from '@angular/core/testing';
import {Subject} from 'rxjs';
import {vi} from 'vitest';
import {SwUpdate, VersionEvent} from '@angular/service-worker';
import {provideTranslateService} from '@ngx-translate/core';
import {AppUpdateService} from './app-update.service';
import {NotificationService} from './notification.service';

describe('AppUpdateService', () => {
  let versionUpdates: Subject<VersionEvent>;
  const notificationService = {showAction: vi.fn().mockName('NotificationService.showAction')};

  const create = (isEnabled: boolean) => {
    TestBed.configureTestingModule({
      providers: [
        provideTranslateService(),
        {provide: SwUpdate, useValue: {isEnabled, versionUpdates}},
        {provide: NotificationService, useValue: notificationService}
      ]
    });
    return TestBed.inject(AppUpdateService);
  };

  beforeEach(() => {
    versionUpdates = new Subject<VersionEvent>();
    notificationService.showAction.mockClear();
  });

  it('should offer a reload once a new version is ready', () => {
    create(true).watch();
    versionUpdates.next({type: 'VERSION_DETECTED', version: {hash: 'b'}});
    expect(notificationService.showAction).not.toHaveBeenCalled();

    versionUpdates.next({type: 'VERSION_READY', currentVersion: {hash: 'a'}, latestVersion: {hash: 'b'}});
    expect(notificationService.showAction).toHaveBeenCalledWith('GLOBAL.UPDATE_READY', 'GLOBAL.RELOAD', expect.any(Function));
  });

  it('should do nothing without a service worker (dev server, tests)', () => {
    create(false).watch();
    versionUpdates.next({type: 'VERSION_READY', currentVersion: {hash: 'a'}, latestVersion: {hash: 'b'}});
    expect(notificationService.showAction).not.toHaveBeenCalled();
  });
});
