import {TestBed} from '@angular/core/testing';
import {ThemeService} from './theme.service';
import {AuthService} from '../../api/antares/services/auth.service';
import {UserService} from '../../api/antares/services/user.service';
import {signal} from '@angular/core';
import {of} from 'rxjs';
import {UserResponse} from '../../api/antares/models/user.model';
import {provideHttpClient} from '@angular/common/http';
import {provideHttpClientTesting} from '@angular/common/http/testing';

describe('ThemeService', () => {
  let service: ThemeService;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let userServiceSpy: jasmine.SpyObj<UserService>;

  const mockUser: UserResponse = {
    id: 1, email: 'test@test.com', firstName: 'Test', lastName: 'User',
    platformRole: 'USER', memberships: [], locale: 'en', theme: 'dark',
    createdAt: ''
  };

  // Helper function to create the test bed
  const configureTestBed = () => {
    const authSpy = jasmine.createSpyObj('AuthService', [], {
      currentUser: signal<UserResponse | null>(null)
    });
    const userSpy = jasmine.createSpyObj('UserService', ['updatePreferences']);

    TestBed.configureTestingModule({
      providers: [
        ThemeService,
        provideHttpClient(),
        provideHttpClientTesting(),
        {provide: AuthService, useValue: authSpy},
        {provide: UserService, useValue: userSpy}
      ]
    });

    service = TestBed.inject(ThemeService);
    authServiceSpy = TestBed.inject(AuthService) as jasmine.SpyObj<AuthService>;
    userServiceSpy = TestBed.inject(UserService) as jasmine.SpyObj<UserService>;
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
      userServiceSpy.updatePreferences.and.returnValue(of({} as UserResponse));

      service.toggleTheme(); // Toggles from 'light' to 'dark'

      expect(userServiceSpy.updatePreferences).toHaveBeenCalledWith({
        locale: 'en',
        theme: 'dark' // The service starts at 'light', so it toggles to 'dark'
      });
    });

    it('should NOT call updatePreferences when no user is logged in', () => {
      (authServiceSpy.currentUser as any).set(null);
      service.toggleTheme();
      expect(userServiceSpy.updatePreferences).not.toHaveBeenCalled();
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
