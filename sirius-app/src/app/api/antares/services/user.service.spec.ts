import type {MockedObject} from "vitest";
import {vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {UserService} from './user.service';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideHttpClient, withXhr} from '@angular/common/http';
import {AuthService} from './auth.service';
import {environment} from '../../../../environments/environment';
import {UserResponse} from '../models/user.model';
import {ChangePasswordRequest} from '../models/auth.model';
import {provideRouter, Router} from '@angular/router';

describe('UserService', () => {
  let service: UserService;
  let httpMock: HttpTestingController;
  let authServiceSpy: MockedObject<AuthService>;
  let router: Router;

  const base = environment.authUrl;
  const mockUser: UserResponse = {
    id: 1, email: 'test@test.com', emailVerified: true, firstName: 'John', lastName: 'Doe',
    platformRole: 'USER', memberships: [], locale: 'en', theme: 'light',
    createdAt: ''
  };

  beforeEach(() => {
    const authSpy = {
      updateCurrentUser: vi.fn().mockName("AuthService.updateCurrentUser")
    };

    TestBed.configureTestingModule({
      providers: [
        UserService,
        provideHttpClient(withXhr()),
        provideHttpClientTesting(),
        provideRouter([]),
        {provide: AuthService, useValue: authSpy}
      ]
    });
    service = TestBed.inject(UserService);
    httpMock = TestBed.inject(HttpTestingController);
    authServiceSpy = TestBed.inject(AuthService) as MockedObject<AuthService>;
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('getProfile should GET from /users/me', () => {
    service.getProfile().subscribe(user => {
      expect(user).toEqual(mockUser);
    });
    const req = httpMock.expectOne(`${base}/users/me`);
    expect(req.request.method).toBe('GET');
    req.flush(mockUser);
  });

  it('updateProfile should PUT and update auth state', () => {
    const payload = {firstName: 'Jane', lastName: 'Doe', email: 'jane@test.com'};
    service.updateProfile(payload).subscribe(user => {
      expect(user).toEqual(mockUser);
    });
    const req = httpMock.expectOne(`${base}/users/me/profile`);
    expect(req.request.method).toBe('PUT');
    req.flush(mockUser);
    expect(authServiceSpy.updateCurrentUser).toHaveBeenCalledWith(mockUser);
  });

  it('updatePreferences should PATCH and update auth state', () => {
    const payload = {locale: 'fr', theme: 'dark' as const};
    service.updatePreferences(payload).subscribe(user => {
      expect(user).toEqual(mockUser);
    });
    const req = httpMock.expectOne(`${base}/users/me/preferences`);
    expect(req.request.method).toBe('PATCH');
    req.flush(mockUser);
    expect(authServiceSpy.updateCurrentUser).toHaveBeenCalledWith(mockUser);
  });

  it('changePassword should PUT to correct URL', () => {
    const payload: ChangePasswordRequest = {
      currentPassword: '1',
      newPassword: '2',
      confirmationPassword: '2'
    };
    service.changePassword(payload).subscribe();
    const req = httpMock.expectOne(`${base}/users/me/password`);
    expect(req.request.method).toBe('PUT');
    req.flush(null);
  });

  it('deleteAccount should DELETE, clear auth state, and redirect', () => {
    const navigateSpy = vi.spyOn(router, 'navigate');

    service.deleteAccount().subscribe();

    const req = httpMock.expectOne(`${base}/users/me`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);

    expect(authServiceSpy.updateCurrentUser).toHaveBeenCalledWith(null);
    expect(navigateSpy).toHaveBeenCalledWith(['/auth/register']);
  });

  it('updateAthleteProfile should PUT and update auth state', () => {
    const payload = {division: 'WOMEN' as const, birthYear: 1992, heightCm: 168};
    service.updateAthleteProfile(payload).subscribe();
    const req = httpMock.expectOne(`${base}/users/me/athlete-profile`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(payload);
    req.flush(mockUser);
    expect(authServiceSpy.updateCurrentUser).toHaveBeenCalledWith(mockUser);
  });

  it('should list, record and delete body weights', () => {
    service.getBodyWeights().subscribe();
    httpMock.expectOne(`${base}/users/me/body-weights`).flush([]);

    service.recordBodyWeight({weightKg: 78.5, measuredOn: '2026-10-28'}).subscribe();
    const post = httpMock.expectOne(`${base}/users/me/body-weights`);
    expect(post.request.method).toBe('POST');
    post.flush({id: 1, measuredOn: '2026-10-28', weightKg: 78.5});

    service.deleteBodyWeight(1).subscribe();
    const del = httpMock.expectOne(`${base}/users/me/body-weights/1`);
    expect(del.request.method).toBe('DELETE');
    del.flush(null);
  });

  it('resendVerification should POST to /users/me/email/verification', () => {
    service.resendVerification().subscribe();
    const req = httpMock.expectOne(`${base}/users/me/email/verification`);
    expect(req.request.method).toBe('POST');
    req.flush(null, {status: 202, statusText: 'Accepted'});
  });
});
