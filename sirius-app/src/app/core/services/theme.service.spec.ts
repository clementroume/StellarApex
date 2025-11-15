import { TestBed } from '@angular/core/testing';
import { ThemeService } from './theme.service';
import { AuthService } from './auth.service';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { User } from '../models/user.model';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('ThemeService', () => {
  let service: ThemeService;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  const mockUser: User = {
    id: 1, email: 'test@test.com', firstName: 'Test', lastName: 'User',
    role: 'ROLE_USER', enabled: true, locale: 'en', theme: 'dark',
    createdAt: '', updatedAt: ''
  };

  // Helper function to create the test bed
  const configureTestBed = () => {
    const authSpy = jasmine.createSpyObj('AuthService', ['updatePreferences'], {
      currentUser: signal<User | null>(null)
    });

    TestBed.configureTestingModule({
      providers: [
        ThemeService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authSpy }
      ]
    });

    service = TestBed.inject(ThemeService);
    authServiceSpy = TestBed.inject(AuthService) as jasmine.SpyObj<AuthService>;
  };

  // afterEach to clean up global state after every single test
  afterEach(() => {
    localStorage.clear();
    document.documentElement.removeAttribute('data-theme');
  });

  describe('with default initialization', () => {
    beforeEach(() => {
      configureTestBed();
    });

    it('should be created', () => {
      expect(service).toBeTruthy();
    });

    it('should initialize with "light" theme by default', () => {
      expect(service.currentTheme()).toBe('light');
    });

    it('should toggle theme from light to dark', () => {
      service.toggleTheme();
      expect(service.currentTheme()).toBe('dark');
    });

    it('should call updatePreferences when a user is logged in', () => {
      (authServiceSpy.currentUser as any).set(mockUser);
      authServiceSpy.updatePreferences.and.returnValue(of({} as User));

      service.toggleTheme(); // Toggles from 'light' to 'dark'

      expect(authServiceSpy.updatePreferences).toHaveBeenCalledWith({
        locale: 'en',
        theme: 'dark' // The service starts at 'light', so it toggles to 'dark'
      });
    });

    it('should NOT call updatePreferences when no user is logged in', () => {
      (authServiceSpy.currentUser as any).set(null);
      service.toggleTheme();
      expect(authServiceSpy.updatePreferences).not.toHaveBeenCalled();
    });
  });

  describe('with localStorage initialization', () => {
    beforeEach(() => {
      // Set localStorage BEFORE the service is created
      localStorage.setItem('theme', 'dark');
      configureTestBed();
    });

    it('should initialize with the theme from localStorage', () => {
      expect(service.currentTheme()).toBe('dark');
    });
  });
});
