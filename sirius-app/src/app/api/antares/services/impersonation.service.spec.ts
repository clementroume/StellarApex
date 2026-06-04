import type {MockedObject} from "vitest";
import {vi} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideHttpClient} from '@angular/common/http';
import {ImpersonationService} from './impersonation.service';
import {AuthService} from './auth.service';
import {environment} from '../../../../environments/environment';
import {UserResponse} from '../models/user.model';

describe('ImpersonationService', () => {
  let service: ImpersonationService;
  let httpMock: HttpTestingController;
  let authServiceSpy: MockedObject<AuthService>;
  const base = environment.authUrl;

  const mockUser: UserResponse = {
    id: 2, email: 'impersonated@test.com', firstName: 'Impersonated', lastName: 'User',
    platformRole: 'USER', memberships: [], locale: 'fr', theme: 'dark',
    createdAt: ''
  };

  beforeEach(() => {
    const authSpy = {
      updateCurrentUser: vi.fn().mockName("AuthService.updateCurrentUser")
    };

    TestBed.configureTestingModule({
      providers: [
        ImpersonationService,
        provideHttpClient(),
        provideHttpClientTesting(),
        {provide: AuthService, useValue: authSpy}
      ]
    });
    service = TestBed.inject(ImpersonationService);
    httpMock = TestBed.inject(HttpTestingController);
    authServiceSpy = TestBed.inject(AuthService) as MockedObject<AuthService>;
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('impersonate should POST to /auth/impersonate/{userId} and update auth state', () => {
    const targetUserId = 2;
    service.impersonate(targetUserId).subscribe(user => {
      expect(user).toEqual(mockUser);
    });

    const req = httpMock.expectOne(`${base}/auth/impersonate/${targetUserId}`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush(mockUser);

    expect(authServiceSpy.updateCurrentUser).toHaveBeenCalledWith(mockUser);
  });
});
