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

  describe('user menu and catalog', () => {
    it('should NOT display ANY dropdown when user is not logged in', () => {
      mockCurrentUser.set(null);
      fixture.detectChanges();

      // On s'attend à ce qu'il y ait 0 dropdown (ni catalogue, ni user menu)
      const dropdowns = fixture.debugElement.queryAll(By.css('.dropdown'));
      expect(dropdowns.length).withContext('No dropdowns should exist when logged out').toBe(0);

      const loginBtn = fixture.debugElement.query(By.css('a[routerLink="/auth/login"]'));
      expect(loginBtn).not.toBeNull();
    });

    it('should display the dropdowns and correct links when user is logged in', () => {
      // Arrange: Simulate a logged-in user
      mockCurrentUser.set(mockUser);
      fixture.detectChanges();

      // On vérifie qu'on a bien les DEUX dropdowns (Catalogue + Utilisateur)
      const dropdowns = fixture.debugElement.queryAll(By.css('.dropdown'));
      expect(dropdowns.length).withContext('Both dropdowns should be visible').toBe(2);

      // Act: Find the link element
      const myAccountLink = fixture.debugElement.query(By.css('a[routerLink="/my-account"]'));
      expect(myAccountLink).withContext('"My Account" link should exist').not.toBeNull();

      // Assert: Check the rendered href attribute
      const linkElement = myAccountLink.nativeElement as HTMLAnchorElement;
      expect(linkElement.getAttribute('href')).toBe('/my-account');
    });
  });
});
