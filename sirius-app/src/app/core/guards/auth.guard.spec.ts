import type {Mock, MockedObject} from "vitest";
import {vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {ActivatedRouteSnapshot, CanActivateFn, provideRouter, Router, RouterStateSnapshot} from '@angular/router';
import {authGuard} from './auth.guard';
import {AuthService} from '../../api/antares/services/auth.service';
import {of} from 'rxjs';
import {UserResponse} from '../../api/antares/models/user.model';

describe('authGuard', () => {
  let authServiceSpy: MockedObject<AuthService>;
  let routerSpy: MockedObject<Router>;

  const executeGuard: CanActivateFn = (route, state) => TestBed.runInInjectionContext(() => authGuard(route, state));

  const dummyRoute = {} as ActivatedRouteSnapshot;
  const dummyState = {url: '/dashboard'} as RouterStateSnapshot;
  const dummyUser = {id: 1} as UserResponse;

  beforeEach(() => {
    const authSpy = {
      initCurrentUser: vi.fn().mockName("AuthService.initCurrentUser"),
      currentUser: vi.fn().mockReturnValue(undefined),
      isAuthenticated: vi.fn().mockReturnValue(false)
    };
    const routerSpyObj = {
      navigate: vi.fn().mockName("Router.navigate")
    };

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {provide: AuthService, useValue: authSpy},
        {provide: Router, useValue: routerSpyObj},
      ],
    });

    authServiceSpy = TestBed.inject(AuthService) as MockedObject<AuthService>;
    routerSpy = TestBed.inject(Router) as MockedObject<Router>;
  });

  it('should allow access if user is authenticated (fast path)', () => {
    (authServiceSpy.currentUser as Mock).mockReturnValue(dummyUser);
    (authServiceSpy.isAuthenticated as Mock).mockReturnValue(true);

    const canActivate = executeGuard(dummyRoute, dummyState);

    expect(canActivate).toBe(true);
    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });

  it('should redirect to login if user is not authenticated (fast path)', () => {
    (authServiceSpy.currentUser as Mock).mockReturnValue(null);
    (authServiceSpy.isAuthenticated as Mock).mockReturnValue(false);

    const canActivate = executeGuard(dummyRoute, dummyState);

    expect(canActivate).toBe(false);
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/auth/login'], {queryParams: {returnUrl: '/dashboard'}});
  });

  it('should initialize and allow access if user is valid (slow path)', async () => {
    authServiceSpy.initCurrentUser.mockReturnValue(of(dummyUser)); // Return a user object
    (authServiceSpy.isAuthenticated as Mock).mockReturnValue(true);

    const canActivate$ = executeGuard(dummyRoute, dummyState) as any;

    canActivate$.subscribe((canActivate: boolean) => {
      expect(canActivate).toBe(true);
    });
  });

  it('should initialize and redirect if user is invalid (slow path)', async () => {
    authServiceSpy.initCurrentUser.mockReturnValue(of(null)); // Return null
    (authServiceSpy.isAuthenticated as Mock).mockReturnValue(false);

    const canActivate$ = executeGuard(dummyRoute, dummyState) as any;

    canActivate$.subscribe((canActivate: boolean) => {
      expect(canActivate).toBe(false);
      expect(routerSpy.navigate).toHaveBeenCalledWith(['/auth/login'], {queryParams: {returnUrl: '/dashboard'}});
    });
  });
});
