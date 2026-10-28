import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideRouter} from '@angular/router';
import {provideTranslateService} from '@ngx-translate/core';
import {provideIcons} from '@ng-icons/core';
import {of, throwError} from 'rxjs';
import {APP_ICONS} from '../../../app.config';
import {AuthService} from '../../../api/antares/services/auth.service';
import {NotificationService} from '../../../core/services/notification.service';
import {ForgotPasswordComponent} from './forgot-password.component';

describe('ForgotPasswordComponent', () => {
  let fixture: ComponentFixture<ForgotPasswordComponent>;
  let component: ForgotPasswordComponent;
  const authService = {forgotPassword: vi.fn().mockName('AuthService.forgotPassword')};
  const notifications = {showError: vi.fn().mockName('NotificationService.showError')};

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [ForgotPasswordComponent],
      providers: [
        provideRouter([]),
        provideIcons(APP_ICONS),
        provideTranslateService(),
        {provide: AuthService, useValue: authService},
        {provide: NotificationService, useValue: notifications}
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(ForgotPasswordComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should send the address and show the same confirmation for any address', () => {
    authService.forgotPassword.mockReturnValue(of(undefined));
    component.form.setValue({email: 'someone@example.com'});

    component.onSubmit();
    fixture.detectChanges();

    expect(authService.forgotPassword).toHaveBeenCalledWith({email: 'someone@example.com'});
    expect(component.sent()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('FORGOT_PASSWORD.SENT');
  });

  it('should not send an invalid address', () => {
    component.form.setValue({email: 'not-an-email'});
    component.onSubmit();
    expect(authService.forgotPassword).not.toHaveBeenCalled();
  });

  it('should show the backend message when throttled', () => {
    authService.forgotPassword.mockReturnValue(throwError(() => ({error: {detail: 'Too many requests'}})));
    component.form.setValue({email: 'someone@example.com'});

    component.onSubmit();

    expect(notifications.showError).toHaveBeenCalledWith('Too many requests');
    expect(component.sent()).toBe(false);
  });
});
