import type {Mock, MockedObject} from "vitest";
import {vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {ActivatedRouteSnapshot, CanActivateFn, provideRouter, Router, RouterStateSnapshot, UrlTree} from '@angular/router';
import {firstValueFrom, Observable, of} from 'rxjs';
import {authGuard} from './auth.guard';
import {AuthService} from '../../api/antares/services/auth.service';
import {UserResponse} from '../../api/antares/models/user.model';

describe('authGuard', () => {
  let authServiceSpy: MockedObject<AuthService>;
  let router: Router;

  const executeGuard: CanActivateFn = (route, state) => TestBed.runInInjectionContext(() => authGuard(route, state));

  const dummyRoute = {} as ActivatedRouteSnapshot;
  const invitation = {url: '/join?gym=4&code=TESTBOX'} as RouterStateSnapshot;
  const dummyUser = {id: 1} as UserResponse;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: AuthService, useValue: {
            initCurrentUser: vi.fn().mockName('AuthService.initCurrentUser'),
            currentUser: vi.fn().mockReturnValue(undefined),
            isAuthenticated: vi.fn().mockReturnValue(false)
          }
        },
      ],
    });
    authServiceSpy = TestBed.inject(AuthService) as MockedObject<AuthService>;
    router = TestBed.inject(Router);
  });

  const expectLoginTree = (result: unknown) => {
    expect(result).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(result as UrlTree))
      .toBe('/auth/login?returnUrl=%2Fjoin%3Fgym%3D4%26code%3DTESTBOX');
  };

  it('should allow access if user is authenticated (fast path)', () => {
    (authServiceSpy.currentUser as Mock).mockReturnValue(dummyUser);
    (authServiceSpy.isAuthenticated as Mock).mockReturnValue(true);

    expect(executeGuard(dummyRoute, invitation)).toBe(true);
  });

  it('should redirect to login with the requested URL if not authenticated (fast path)', () => {
    (authServiceSpy.currentUser as Mock).mockReturnValue(null);

    expectLoginTree(executeGuard(dummyRoute, invitation));
  });

  it('should initialize and allow access if user is valid (slow path)', async () => {
    authServiceSpy.initCurrentUser.mockReturnValue(of(dummyUser));
    (authServiceSpy.isAuthenticated as Mock).mockReturnValue(true);

    expect(await firstValueFrom(executeGuard(dummyRoute, invitation) as Observable<unknown>)).toBe(true);
  });

  it('should initialize and redirect if user is invalid (slow path)', async () => {
    authServiceSpy.initCurrentUser.mockReturnValue(of(null));

    expectLoginTree(await firstValueFrom(executeGuard(dummyRoute, invitation) as Observable<unknown>));
  });
});
