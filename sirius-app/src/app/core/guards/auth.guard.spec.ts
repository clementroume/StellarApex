import {TestBed} from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  CanActivateFn,
  provideRouter,
  Router,
  RouterStateSnapshot
} from '@angular/router';
import {authGuard} from './auth.guard';
import {AuthService} from '../../api/antares/services/auth.service';
import {of} from 'rxjs';
import {UserResponse} from '../../api/antares/models/user.model';

describe('authGuard', () => {
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let routerSpy: jasmine.SpyObj<Router>;

  // Helper to execute the guard. It now matches the CanActivateFn signature.
  const executeGuard: CanActivateFn = (route, state) =>
    TestBed.runInInjectionContext(() => authGuard(route, state));

  const dummyRoute = {} as ActivatedRouteSnapshot;
  const dummyState = {url: '/dashboard'} as RouterStateSnapshot;
  const dummyUser = {id: 1} as UserResponse;

  beforeEach(() => {
    const authSpy = jasmine.createSpyObj('AuthService', ['initCurrentUser'], {
      currentUser: jasmine.createSpy('currentUser').and.returnValue(undefined),
      isAuthenticated: jasmine.createSpy('isAuthenticated').and.returnValue(false)
    });
    const routerSpyObj = jasmine.createSpyObj('Router', ['navigate']);

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {provide: AuthService, useValue: authSpy},
        {provide: Router, useValue: routerSpyObj},
      ],
    });

    authServiceSpy = TestBed.inject(AuthService) as jasmine.SpyObj<AuthService>;
    routerSpy = TestBed.inject(Router) as jasmine.SpyObj<Router>;
  });

  it('should allow access if user is authenticated (fast path)', () => {
    (authServiceSpy.currentUser as jasmine.Spy).and.returnValue(dummyUser);
    (authServiceSpy.isAuthenticated as jasmine.Spy).and.returnValue(true);

    const canActivate = executeGuard(dummyRoute, dummyState);

    expect(canActivate).toBeTrue();
    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });

  it('should redirect to login if user is not authenticated (fast path)', () => {
    (authServiceSpy.currentUser as jasmine.Spy).and.returnValue(null);
    (authServiceSpy.isAuthenticated as jasmine.Spy).and.returnValue(false);

    const canActivate = executeGuard(dummyRoute, dummyState);

    expect(canActivate).toBeFalse();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/auth/login'], {queryParams: {returnUrl: '/dashboard'}});
  });

  it('should initialize and allow access if user is valid (slow path)', (done) => {
    authServiceSpy.initCurrentUser.and.returnValue(of(dummyUser)); // Return a user object
    (authServiceSpy.isAuthenticated as jasmine.Spy).and.returnValue(true);

    const canActivate$ = executeGuard(dummyRoute, dummyState) as any;

    canActivate$.subscribe((canActivate: boolean) => {
      expect(canActivate).toBeTrue();
      done();
    });
  });

  it('should initialize and redirect if user is invalid (slow path)', (done) => {
    authServiceSpy.initCurrentUser.and.returnValue(of(null)); // Return null
    (authServiceSpy.isAuthenticated as jasmine.Spy).and.returnValue(false);

    const canActivate$ = executeGuard(dummyRoute, dummyState) as any;

    canActivate$.subscribe((canActivate: boolean) => {
      expect(canActivate).toBeFalse();
      expect(routerSpy.navigate).toHaveBeenCalledWith(['/auth/login'], {queryParams: {returnUrl: '/dashboard'}});
      done();
    });
  });
});
