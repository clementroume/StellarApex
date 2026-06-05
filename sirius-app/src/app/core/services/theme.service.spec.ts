import type {MockedObject} from "vitest";
import {vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {ThemeService} from './theme.service';
import {AuthService} from '../../api/antares/services/auth.service';
import {UserService} from '../../api/antares/services/user.service';
import {signal} from '@angular/core';
import {of} from 'rxjs';
import {UserResponse} from '../../api/antares/models/user.model';
import {provideHttpClient, withXhr} from '@angular/common/http';
import {provideHttpClientTesting} from '@angular/common/http/testing';

const fakeLocalStorage = (() => {
  let store: Record<string, string> = {};
  return {
    getItem: vi.fn((key: string) => store[key] || null),
    setItem: vi.fn((key: string, value: string) => {
      store[key] = value.toString();
    }),
    clear: vi.fn(() => {
      store = {};
    })
  };
})();
vi.stubGlobal('localStorage', fakeLocalStorage);

describe('ThemeService', () => {
  let service: ThemeService;
  let authServiceSpy: MockedObject<AuthService>;
  let userServiceSpy: MockedObject<UserService>;

  const mockUser: UserResponse = {
    id: 1, email: 'test@test.com', firstName: 'Test', lastName: 'User',
    platformRole: 'USER', memberships: [], locale: 'en', theme: 'dark',
    createdAt: ''
  };

  // Helper function to create the test bed
  const configureTestBed = () => {
    const authSpy = {
      currentUser: signal<UserResponse | null>(null)
    };
    const userSpy = {
      updatePreferences: vi.fn().mockName("UserService.updatePreferences")
    };

    TestBed.configureTestingModule({
      providers: [
        ThemeService,
        provideHttpClient(withXhr()),
        provideHttpClientTesting(),
        {provide: AuthService, useValue: authSpy},
        {provide: UserService, useValue: userSpy}
      ]
    });

    service = TestBed.inject(ThemeService);
    authServiceSpy = TestBed.inject(AuthService) as MockedObject<AuthService>;
    userServiceSpy = TestBed.inject(UserService) as MockedObject<UserService>;
  };

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
      expect(service.currentTheme()).toBe('dark');
    });

    it('should toggle theme from light to dark', () => {
      service.toggleTheme();
      expect(service.currentTheme()).toBe('light');
    });

    it('should call updatePreferences when a user is logged in', () => {
      (authServiceSpy.currentUser as any).set(mockUser);
      userServiceSpy.updatePreferences.mockReturnValue(of({} as UserResponse));

      service.toggleTheme(); // Toggles from 'light' to 'dark'

      expect(userServiceSpy.updatePreferences).toHaveBeenCalledWith({
        locale: 'en',
        theme: 'light' // The service starts at 'light', so it toggles to 'dark'
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
