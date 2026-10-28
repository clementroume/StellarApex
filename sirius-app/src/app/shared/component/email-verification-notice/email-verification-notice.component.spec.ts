import {ComponentFixture, TestBed} from '@angular/core/testing';
import {signal} from '@angular/core';
import {provideTranslateService} from '@ngx-translate/core';
import {provideIcons} from '@ng-icons/core';
import {of} from 'rxjs';
import {APP_ICONS} from '../../../app.config';
import {AuthService} from '../../../api/antares/services/auth.service';
import {UserService} from '../../../api/antares/services/user.service';
import {UserResponse} from '../../../api/antares/models/user.model';
import {NotificationService} from '../../../core/services/notification.service';
import {EmailVerificationNoticeComponent} from './email-verification-notice.component';

describe('EmailVerificationNoticeComponent', () => {
  let fixture: ComponentFixture<EmailVerificationNoticeComponent>;
  const currentUser = signal<Partial<UserResponse> | null>(null);
  const userService = {resendVerification: vi.fn().mockName('UserService.resendVerification')};
  const notifications = {
    showSuccess: vi.fn().mockName('NotificationService.showSuccess'),
    showError: vi.fn().mockName('NotificationService.showError')
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [EmailVerificationNoticeComponent],
      providers: [
        provideIcons(APP_ICONS),
        provideTranslateService(),
        {provide: AuthService, useValue: {currentUser}},
        {provide: UserService, useValue: userService},
        {provide: NotificationService, useValue: notifications}
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(EmailVerificationNoticeComponent);
  });

  it('should show nothing for a verified address', () => {
    currentUser.set({email: 'a@b.c', emailVerified: true});
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-slot="email-verification-notice"]')).toBeNull();
  });

  it('should explain and resend the link for an unverified address', () => {
    currentUser.set({email: 'a@b.c', emailVerified: false});
    userService.resendVerification.mockReturnValue(of(undefined));
    fixture.detectChanges();

    const notice: HTMLElement = fixture.nativeElement.querySelector('[data-slot="email-verification-notice"]');
    expect(notice.textContent).toContain('EMAIL_VERIFICATION.TITLE');
    notice.querySelector('button')!.click();

    expect(userService.resendVerification).toHaveBeenCalled();
    expect(notifications.showSuccess).toHaveBeenCalled();
  });
});
