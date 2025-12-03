import {TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideRouter, Router} from '@angular/router';
import {AuthService} from './auth.service';
import {AuthenticationRequest, ChangePasswordRequest, User} from '../models/user.model';
import {environment} from '../../../environments/environment';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;
  let router: Router;
  let navigateSpy: jasmine.Spy;

  const base = `${environment.authUrl}`;

  const dummyUser: User = {
    id: 1,
    firstName: 'John',
    lastName: 'Doe',
    email: 'john.doe@test.com',
    role: 'ROLE_USER',
    enabled: true,
    locale: 'en',
    theme: 'light',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString()
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        AuthService
      ]
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    navigateSpy = spyOn(router, 'navigate').and.resolveTo(true);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created with initial state', () => {
    expect(service).toBeTruthy();
    expect(service.currentUser()).toBeUndefined();
    expect(service.isAuthenticated()).toBeFalse();
  });

  describe('initCurrentUser', () => {
    it('should fetch user and set signals on success', () => {
      service.initCurrentUser().subscribe();
      const req = httpMock.expectOne(`${base}/users/me`);
      req.flush(dummyUser);
      expect(service.currentUser()).toEqual(dummyUser);
      expect(service.isAuthenticated()).toBeTrue();
    });

    it('should set user to null on error', () => {
      service.initCurrentUser().subscribe();
      const req = httpMock.expectOne(`${base}/users/me`);
      req.flush({}, {status: 401, statusText: 'Unauthorized'});
      expect(service.currentUser()).toBeNull();
      expect(service.isAuthenticated()).toBeFalse();
    });
  });

  describe('login', () => {
    it('should POST and set current user', () => {
      const payload: AuthenticationRequest = {email: 'john.doe@test.com', password: 'secret'};
      service.login(payload).subscribe();
      const req = httpMock.expectOne(`${base}/auth/login`);
      req.flush(dummyUser);
      expect(service.currentUser()).toEqual(dummyUser);
    });
  });

  describe('logout', () => {
    it('should POST, clear state, and navigate on success', () => {
      (service as any)._currentUser.set(dummyUser);
      service.logout().subscribe();
      const req = httpMock.expectOne(`${base}/auth/logout`);
      req.flush(null);
      expect(service.currentUser()).toBeNull();
      expect(navigateSpy).toHaveBeenCalledWith(['/auth/login']);
    });

    it('should clear state and navigate even if API errors', () => {
      (service as any)._currentUser.set(dummyUser);
      service.logout().subscribe();
      const req = httpMock.expectOne(`${base}/auth/logout`);
      req.flush({}, {status: 500, statusText: 'Server Error'});
      expect(service.currentUser()).toBeNull();
      expect(navigateSpy).toHaveBeenCalledWith(['/auth/login']);
    });
  });

  describe('changePassword', () => {
    it('should PUT to correct URL and emit null on success', () => {
      const payload: ChangePasswordRequest = {
        currentPassword: '1',
        newPassword: '2',
        confirmationPassword: '2'
      };
      service.changePassword(payload).subscribe(res => {
        expect(res).toBeNull();
      });
      const req = httpMock.expectOne(`${base}/users/me/password`);
      expect(req.request.method).toBe('PUT');
      req.flush(null, {status: 200, statusText: 'OK'});
    });
  });

  describe('deleteAccount', () => {
    it('should DELETE to correct URL, clear user state, and navigate to register', () => {
      // Given: A user is logged in
      (service as any)._currentUser.set(dummyUser);

      // When
      service.deleteAccount().subscribe();

      // Then: Verify the HTTP request
      const req = httpMock.expectOne(`${base}/users/me`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null, {status: 204, statusText: 'No Content'});

      // Verify state clearing
      expect(service.currentUser()).toBeNull();

      // Verify redirection
      expect(navigateSpy).toHaveBeenCalledWith(['/auth/register']);
    });
  });
});
