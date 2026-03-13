import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NavbarComponent} from './navbar.component';
import {provideRouter} from '@angular/router';
import {TranslateModule} from '@ngx-translate/core';
import {AuthService} from '../../../api/antares/services/auth.service';
import {ThemeService} from '../../services/theme.service';
import {of} from 'rxjs';
import {signal, WritableSignal} from '@angular/core';
import {UserResponse} from '../../../api/antares/models/user.model';
import {By} from '@angular/platform-browser';
import {provideIcons} from '@ng-icons/core';
import {APP_ICONS} from '../../../app.config';

describe('NavbarComponent', () => {
  let component: NavbarComponent;
  let fixture: ComponentFixture<NavbarComponent>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let themeServiceSpy: jasmine.SpyObj<ThemeService>;
  let mockCurrentUser: WritableSignal<UserResponse | null>;

  const mockUser: UserResponse = {
    id: 1, email: 'test@test.com', firstName: 'John', lastName: 'Doe',
    platformRole: 'USER', memberships: [], locale: 'en', theme: 'light',
    createdAt: ''
  };

  beforeEach(async () => {
    mockCurrentUser = signal(null);

    authServiceSpy = jasmine.createSpyObj('AuthService', ['logout'], {
      currentUser: mockCurrentUser.asReadonly()
    });
    themeServiceSpy = jasmine.createSpyObj('ThemeService', ['toggleTheme'], {
      currentTheme: signal('light')
    });

    await TestBed.configureTestingModule({
      imports: [NavbarComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        provideIcons(APP_ICONS),
        {provide: AuthService, useValue: authServiceSpy},
        {provide: ThemeService, useValue: themeServiceSpy},
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(NavbarComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should call authService.logout when logout() is called', () => {
    authServiceSpy.logout.and.returnValue(of(undefined));
    component.logout();
    expect(authServiceSpy.logout).toHaveBeenCalledTimes(1);
  });

  it('should call themeService.toggleTheme when the theme toggle is changed', () => {
    const themeToggleInput = fixture.nativeElement.querySelector('#theme-toggle');
    expect(themeToggleInput).withContext('Theme toggle input should exist').toBeTruthy();

    themeToggleInput.dispatchEvent(new Event('change'));

    expect(themeServiceSpy.toggleTheme).toHaveBeenCalledTimes(1);
  });

  it('should display the mobile dropdown and correct links when user is logged in', () => {
    mockCurrentUser.set(mockUser);
    fixture.detectChanges();

    const dropdowns = fixture.debugElement.queryAll(By.css('.dropdown'));
    expect(dropdowns.length).withContext('Only the mobile dropdown should be visible').toBe(1);

    const myAccountLink = fixture.debugElement.query(By.css('a[routerLink="/my-account"]'));
    expect(myAccountLink).withContext('"My Account" link should exist').not.toBeNull();

    const linkElement = myAccountLink.nativeElement as HTMLAnchorElement;
    expect(linkElement.getAttribute('href')).toBe('/my-account');
  });
});
