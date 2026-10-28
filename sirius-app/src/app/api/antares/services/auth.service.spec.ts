import type {Mock} from "vitest";
import {vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {provideHttpClient, withXhr} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideRouter, Router} from '@angular/router';
import {AuthService} from './auth.service';
import {environment} from '../../../../environments/environment';
import {AuthenticationRequest} from '../models/auth.model';
import {UserResponse} from '../models/user.model';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;
  let router: Router;
  let navigateSpy: Mock;

  const base = `${environment.authUrl}`;

  const dummyUser: UserResponse = {
    id: 1,
    firstName: 'John',
    lastName: 'Doe',
    email: 'john.doe@test.com',
    emailVerified: true,
    platformRole: 'USER',
    locale: 'en',
    theme: 'light',
    createdAt: new Date().toISOString(),
    memberships: []
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withXhr()),
        provideHttpClientTesting(),
        provideRouter([]),
        AuthService
      ]
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created with initial state', () => {
    expect(service).toBeTruthy();
    expect(service.currentUser()).toBeUndefined();
    expect(service.isAuthenticated()).toBe(false);
  });

  describe('initCurrentUser', () => {
    it('should fetch user and set signals on success', () => {
      service.initCurrentUser().subscribe();
      const req = httpMock.expectOne(`${base}/users/me`);
      req.flush(dummyUser);
      expect(service.currentUser()).toEqual(dummyUser);
      expect(service.isAuthenticated()).toBe(true);
    });

    it('should set user to null on error', () => {
      service.initCurrentUser().subscribe();
      const req = httpMock.expectOne(`${base}/users/me`);
      req.flush({}, {status: 401, statusText: 'Unauthorized'});
      expect(service.currentUser()).toBeNull();
      expect(service.isAuthenticated()).toBe(false);
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

  describe('updateCurrentUser', () => {
    const session = {id: 3, actor: {id: 9, firstName: 'Ad', lastName: 'Min'}, reason: 'Bug', expiresAt: '2026-10-25T12:00:00Z'};

    it('should keep the support session when the response does not carry it', () => {
      service.updateCurrentUser({...dummyUser, supportSession: session});

      service.updateCurrentUser({...dummyUser, locale: 'fr'});

      expect(service.currentUser()?.locale).toBe('fr');
      expect(service.currentUser()?.supportSession).toEqual(session);
    });

    it('should apply an explicit end of session', () => {
      service.updateCurrentUser({...dummyUser, supportSession: session});

      service.updateCurrentUser({...dummyUser, supportSession: null});

      expect(service.currentUser()?.supportSession).toBeNull();
    });

    it('should never carry a session over to another user', () => {
      service.updateCurrentUser({...dummyUser, supportSession: session});

      service.updateCurrentUser({...dummyUser, id: 2});

      expect(service.currentUser()?.supportSession).toBeUndefined();
    });
  });

  describe('account e-mails', () => {
    it('should POST the forgot, reset and verify requests', () => {
      service.forgotPassword({email: 'a@b.c'}).subscribe();
      const forgot = httpMock.expectOne(`${environment.authUrl}/auth/password/forgot`);
      expect(forgot.request.method).toBe('POST');
      expect(forgot.request.body).toEqual({email: 'a@b.c'});
      forgot.flush(null, {status: 202, statusText: 'Accepted'});

      const reset = {token: 't', newPassword: 'Secret123', confirmationPassword: 'Secret123'};
      service.resetPassword(reset).subscribe();
      const resetReq = httpMock.expectOne(`${environment.authUrl}/auth/password/reset`);
      expect(resetReq.request.body).toEqual(reset);
      resetReq.flush(null, {status: 204, statusText: 'No Content'});

      service.verifyEmail({token: 't'}).subscribe();
      const verify = httpMock.expectOne(`${environment.authUrl}/auth/email/verify`);
      expect(verify.request.body).toEqual({token: 't'});
      verify.flush(null, {status: 204, statusText: 'No Content'});
    });
  });
});
