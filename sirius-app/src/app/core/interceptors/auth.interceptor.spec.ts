import {TestBed} from '@angular/core/testing';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {
  HttpClient,
  HttpErrorResponse,
  provideHttpClient,
  withInterceptors
} from '@angular/common/http';
import {authInterceptor} from './auth.interceptor';
import {AuthService} from '../../api/antares/services/auth.service';
import {Router} from '@angular/router';
import {of, throwError} from 'rxjs';
import {environment} from '../../../environments/environment';

describe('authInterceptor', () => {
  let httpMock: HttpTestingController;
  let httpClient: HttpClient;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let routerSpy: jasmine.SpyObj<Router>;
  const baseApiUrl = `${environment.authUrl}`;

  beforeEach(() => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['refreshToken', 'logout']);
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        {provide: AuthService, useValue: authServiceSpy},
        {provide: Router, useValue: routerSpy},
      ],
    });

    httpMock = TestBed.inject(HttpTestingController);
    httpClient = TestBed.inject(HttpClient);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should add withCredentials to API requests', () => {
    httpClient.get(`${baseApiUrl}/users/me`).subscribe();
    const req = httpMock.expectOne(`${baseApiUrl}/users/me`);
    expect(req.request.withCredentials).toBe(true);
    req.flush({});
  });

  it('should NOT add withCredentials to non-API requests', () => {
    httpClient.get('/assets/i18n/en.json').subscribe();
    const req = httpMock.expectOne('/assets/i18n/en.json');
    expect(req.request.withCredentials).toBe(false);
    req.flush({});
  });

  it('should re-throw non-401 errors', () => {
    httpClient.get(`${baseApiUrl}/users/me`).subscribe({
      error: (err: HttpErrorResponse) => {
        expect(err.status).toBe(500);
      }
    });

    const req = httpMock.expectOne(`${baseApiUrl}/users/me`);
    req.flush({}, {status: 500, statusText: 'Server Error'});
    expect(authServiceSpy.refreshToken).not.toHaveBeenCalled();
  });

  it('should retry the original request after a successful token refresh', () => {
    authServiceSpy.refreshToken.and.returnValue(of({accessToken: 'new-token'}));

    httpClient.get(`${baseApiUrl}/users/me`).subscribe();

    // 1. The first, original request fails
    const firstReq = httpMock.expectOne(`${baseApiUrl}/users/me`);
    firstReq.flush({}, {status: 401, statusText: 'Unauthorized'});

    // 2. The interceptor should have called refreshToken
    expect(authServiceSpy.refreshToken).toHaveBeenCalledTimes(1);

    // 3. The original request should be retried automatically
    const retryReq = httpMock.expectOne(`${baseApiUrl}/users/me`);
    retryReq.flush({id: 1, name: 'Test'}); // Complete the retried request
  });

  it('should logout and redirect if token refresh fails', () => {
    authServiceSpy.refreshToken.and.returnValue(throwError(() => new HttpErrorResponse({status: 401})));
    authServiceSpy.logout.and.returnValue(of(undefined));

    httpClient.get(`${baseApiUrl}/users/me`).subscribe({
      error: (err) => {
        expect(err.status).toBe(401);
      }
    });

    const firstReq = httpMock.expectOne(`${baseApiUrl}/users/me`);
    firstReq.flush({}, {status: 401, statusText: 'Unauthorized'});

    expect(authServiceSpy.refreshToken).toHaveBeenCalledTimes(1);
    expect(authServiceSpy.logout).toHaveBeenCalledTimes(1);
  });
});
