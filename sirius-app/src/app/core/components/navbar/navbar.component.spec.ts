import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NavbarComponent } from './navbar.component';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { AuthService } from '../../services/auth.service';
import { ThemeService } from '../../services/theme.service';
import { of } from 'rxjs';
import { signal, WritableSignal } from '@angular/core';
import { User } from '../../models/user.model';
import { By } from '@angular/platform-browser';

describe('NavbarComponent', () => {
  let component: NavbarComponent;
  let fixture: ComponentFixture<NavbarComponent>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let themeServiceSpy: jasmine.SpyObj<ThemeService>;
  let mockCurrentUser: WritableSignal<User | null>;

  const mockUser: User = {
    id: 1, email: 'test@test.com', firstName: 'John', lastName: 'Doe',
    role: 'ROLE_USER', enabled: true, locale: 'en', theme: 'light',
    createdAt: '', updatedAt: ''
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
        { provide: AuthService, useValue: authServiceSpy },
        { provide: ThemeService, useValue: themeServiceSpy },
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

  describe('user menu', () => {
    it('should NOT display the user dropdown when user is not logged in', () => {
      mockCurrentUser.set(null);
      fixture.detectChanges();
      const dropdown = fixture.nativeElement.querySelector('.dropdown');
      expect(dropdown).toBeNull();
    });

    it('should display the "My Account" link with the correct href when user is logged in', () => {
      // Arrange: Simulate a logged-in user
      mockCurrentUser.set(mockUser);
      fixture.detectChanges();

      // Act: Find the link element
      const myAccountLink = fixture.debugElement.query(By.css('a[routerLink="/my-account"]'));
      expect(myAccountLink).withContext('"My Account" link should exist').not.toBeNull();

      // Assert: Check the rendered href attribute, which is the standard way to test routerLink
      const linkElement = myAccountLink.nativeElement as HTMLAnchorElement;
      expect(linkElement.getAttribute('href')).toBe('/my-account');
    });
  });
});
