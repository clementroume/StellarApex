import {TestBed} from '@angular/core/testing';
import {signal, WritableSignal} from '@angular/core';
import {Router} from '@angular/router';
import {provideTranslateService} from '@ngx-translate/core';
import {of, throwError} from 'rxjs';
import {vi} from 'vitest';
import {SupportSessionService} from './support-session.service';
import {AuthService} from '../../api/antares/services/auth.service';
import {AdminService} from '../../api/antares/services/admin.service';
import {NotificationService} from './notification.service';

describe('SupportSessionService', () => {
  let service: SupportSessionService;
  let currentUser: WritableSignal<any>;
  let authService: any;
  let adminService: any;
  let notificationService: any;
  let router: any;

  const session = {id: 3, actor: {id: 1, firstName: 'Ad', lastName: 'Min'}, reason: 'Bug', expiresAt: '2026-10-25T12:00:00'};

  beforeEach(() => {
    currentUser = signal({id: 1, platformRole: 'ADMIN', supportSession: null});
    authService = {currentUser, initCurrentUser: vi.fn().mockReturnValue(of({}))};
    adminService = {
      startSupportSession: vi.fn().mockReturnValue(of({})),
      endSupportSession: vi.fn().mockReturnValue(of(undefined))
    };
    notificationService = {showError: vi.fn()};
    router = {navigate: vi.fn().mockResolvedValue(true)};

    TestBed.configureTestingModule({
      providers: [
        provideTranslateService(),
        {provide: AuthService, useValue: authService},
        {provide: AdminService, useValue: adminService},
        {provide: NotificationService, useValue: notificationService},
        {provide: Router, useValue: router}
      ]
    });
    service = TestBed.inject(SupportSessionService);
  });

  it('should expose the session of the current user', () => {
    expect(service.isActive()).toBe(false);

    currentUser.set({id: 7, platformRole: 'USER', supportSession: session});

    expect(service.isActive()).toBe(true);
    expect(service.session()).toEqual(session);
  });

  it('should start a session, reload the current user and go to the dashboard', () => {
    service.start(7, 'Bug').subscribe();

    expect(adminService.startSupportSession).toHaveBeenCalledWith({userId: 7, reason: 'Bug'});
    expect(authService.initCurrentUser).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/today']);
  });

  it('should not reload the user when the session cannot start', () => {
    adminService.startSupportSession.mockReturnValue(throwError(() => ({status: 403})));
    service.start(7, 'Bug').subscribe({error: () => undefined});
    expect(authService.initCurrentUser).not.toHaveBeenCalled();
  });

  it('should end the session and reload the admin identity, even if the server call fails', () => {
    adminService.endSupportSession.mockReturnValue(throwError(() => ({status: 500})));

    service.end().subscribe();

    expect(authService.initCurrentUser).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/admin/support']);
  });

  it('should handle an expiry once and notify the admin', () => {
    service.handleExpired();
    expect(authService.initCurrentUser).toHaveBeenCalledTimes(1);
    expect(notificationService.showError).toHaveBeenCalledWith('ADMIN.SUPPORT.EXPIRED');
    expect(router.navigate).toHaveBeenCalledWith(['/admin/support']);
  });

  it('should ignore concurrent expiry notifications', () => {
    authService.initCurrentUser.mockReturnValue(new (class {
      subscribe() {
        return {unsubscribe: () => undefined};
      }
    })());

    service.handleExpired();
    service.handleExpired();

    expect(authService.initCurrentUser).toHaveBeenCalledTimes(1);
  });
});
