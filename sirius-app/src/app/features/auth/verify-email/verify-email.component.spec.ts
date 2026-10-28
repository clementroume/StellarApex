import {TestBed} from '@angular/core/testing';
import {DOCUMENT, signal} from '@angular/core';
import {provideRouter} from '@angular/router';
import {provideTranslateService} from '@ngx-translate/core';
import {provideIcons} from '@ng-icons/core';
import {of, throwError} from 'rxjs';
import {APP_ICONS} from '../../../app.config';
import {AuthService} from '../../../api/antares/services/auth.service';
import {UserService} from '../../../api/antares/services/user.service';
import {VerifyEmailComponent} from './verify-email.component';

describe('VerifyEmailComponent', () => {
  const TOKEN = 'AbCdEfGhIjKlMnOpQrStUvWxYz0123456789_-abcd';
  const currentUser = signal<unknown>(undefined);
  const authService = {
    verifyEmail: vi.fn().mockName('AuthService.verifyEmail'),
    initCurrentUser: vi.fn().mockName('AuthService.initCurrentUser'),
    currentUser,
    isAuthenticated: () => !!currentUser()
  };
  const userService = {refreshCurrentUser: vi.fn().mockName('UserService.refreshCurrentUser')};

  const create = async (hash: string) => {
    vi.clearAllMocks();
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [VerifyEmailComponent],
      providers: [
        provideRouter([]),
        provideIcons(APP_ICONS),
        provideTranslateService(),
        {provide: AuthService, useValue: authService},
        {provide: UserService, useValue: userService}
      ]
    }).compileComponents();
    TestBed.inject(DOCUMENT).defaultView!.history.replaceState(null, '', '/auth/verify-email' + hash);
    const fixture = TestBed.createComponent(VerifyEmailComponent);
    fixture.detectChanges();
    return fixture;
  };

  it('should verify the address and load the user when the link opens a new tab', async () => {
    currentUser.set(undefined);
    authService.verifyEmail.mockReturnValue(of(undefined));
    authService.initCurrentUser.mockReturnValue(of(null));

    const fixture = await create('#token=' + TOKEN);

    expect(authService.verifyEmail).toHaveBeenCalledWith({token: TOKEN});
    expect(fixture.componentInstance.state()).toBe('verified');
    expect(authService.initCurrentUser).toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('VERIFY_EMAIL.VERIFIED_TITLE');
  });

  it('should refresh a signed-in user so the box/prog actions unlock', async () => {
    currentUser.set({id: 1});
    authService.verifyEmail.mockReturnValue(of(undefined));
    userService.refreshCurrentUser.mockReturnValue(of({}));

    await create('#token=' + TOKEN);

    expect(userService.refreshCurrentUser).toHaveBeenCalled();
  });

  it('should explain an invalid or missing link', async () => {
    currentUser.set(null);
    authService.verifyEmail.mockReturnValue(throwError(() => ({error: {detail: 'Expired link'}})));

    const fixture = await create('#token=' + TOKEN);
    expect(fixture.componentInstance.state()).toBe('failed');
    expect(fixture.nativeElement.textContent).toContain('Expired link');

    const empty = await create('');
    expect(empty.componentInstance.state()).toBe('failed');
    expect(authService.verifyEmail).not.toHaveBeenCalled();
  });
});
