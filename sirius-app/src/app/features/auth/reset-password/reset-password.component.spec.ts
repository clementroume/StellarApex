import {ComponentFixture, TestBed} from '@angular/core/testing';
import {DOCUMENT} from '@angular/core';
import {provideRouter, Router} from '@angular/router';
import {provideTranslateService} from '@ngx-translate/core';
import {provideIcons} from '@ng-icons/core';
import {of, throwError} from 'rxjs';
import {APP_ICONS} from '../../../app.config';
import {AuthService} from '../../../api/antares/services/auth.service';
import {NotificationService} from '../../../core/services/notification.service';
import {ResetPasswordComponent} from './reset-password.component';

describe('ResetPasswordComponent', () => {
  let fixture: ComponentFixture<ResetPasswordComponent>;
  let component: ResetPasswordComponent;
  const authService = {
    resetPassword: vi.fn().mockName('AuthService.resetPassword'),
    updateCurrentUser: vi.fn().mockName('AuthService.updateCurrentUser')
  };
  const notifications = {
    showSuccess: vi.fn().mockName('NotificationService.showSuccess'),
    showError: vi.fn().mockName('NotificationService.showError')
  };

  const init = async (hash: string) => {
    vi.clearAllMocks();
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [ResetPasswordComponent],
      providers: [
        provideRouter([]),
        provideIcons(APP_ICONS),
        provideTranslateService(),
        {provide: AuthService, useValue: authService},
        {provide: NotificationService, useValue: notifications}
      ]
    }).compileComponents();
    TestBed.inject(DOCUMENT).defaultView!.history.replaceState(null, '', '/auth/reset-password' + hash);
    fixture = TestBed.createComponent(ResetPasswordComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  };

  it('should offer a new link when the page has no token', async () => {
    await init('');
    expect(component.token()).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('RESET_PASSWORD.INVALID_LINK');
  });

  it('should reset the password with the token, forget the user and go to sign-in', async () => {
    await init('#token=AbCdEfGhIjKlMnOpQrStUvWxYz0123456789_-abcd');
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    authService.resetPassword.mockReturnValue(of(undefined));
    component.form.setValue({password: 'NewSecret1', confirmationPassword: 'NewSecret1'});

    component.onSubmit();

    expect(authService.resetPassword).toHaveBeenCalledWith({
      token: 'AbCdEfGhIjKlMnOpQrStUvWxYz0123456789_-abcd', newPassword: 'NewSecret1', confirmationPassword: 'NewSecret1'
    });
    expect(authService.updateCurrentUser).toHaveBeenCalledWith(null);
    expect(navigate).toHaveBeenCalledWith(['/auth/login']);
  });

  it('should refuse mismatching passwords and show a backend error', async () => {
    await init('#token=AbCdEfGhIjKlMnOpQrStUvWxYz0123456789_-abcd');
    component.form.setValue({password: 'NewSecret1', confirmationPassword: 'NewSecret2'});
    component.onSubmit();
    expect(authService.resetPassword).not.toHaveBeenCalled();

    authService.resetPassword.mockReturnValue(throwError(() => ({error: {detail: 'Invalid link'}})));
    component.form.setValue({password: 'NewSecret1', confirmationPassword: 'NewSecret1'});
    component.onSubmit();
    expect(notifications.showError).toHaveBeenCalledWith('Invalid link');
    expect(component.saving()).toBe(false);
  });
});
