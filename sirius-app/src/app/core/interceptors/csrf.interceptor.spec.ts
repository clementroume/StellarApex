import {TestBed} from '@angular/core/testing';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {HttpClient, provideHttpClient, withInterceptors, withNoXsrfProtection} from '@angular/common/http';
import {csrfInterceptor} from './csrf.interceptor';
import {environment} from '../../../environments/environment';

describe('csrfInterceptor', () => {
  let httpMock: HttpTestingController;
  let httpClient: HttpClient;
  const baseApiUrl = `${environment.apiUrl}/api/v1`;

  function setCookie(name: string, value: string) {
    document.cookie = `${name}=${value}`;
  }

  function clearCookies() {
    document.cookie.split(';').forEach(c => {
      document.cookie = c.replace(/^ +/, '').replace(/=.*/, `=;expires=${new Date().toUTCString()};path=/`);
    });
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(
          withInterceptors([csrfInterceptor]),
          withNoXsrfProtection()),
        provideHttpClientTesting(),
      ],
    });

    httpMock = TestBed.inject(HttpTestingController);
    httpClient = TestBed.inject(HttpClient);
    clearCookies();
  });

  afterEach(() => {
    httpMock.verify();
    clearCookies();
  });

  it('should add X-XSRF-TOKEN header to a POST request', () => {
    const csrfToken = 'test-csrf-token';
    setCookie('XSRF-TOKEN', csrfToken);

    httpClient.post(`${baseApiUrl}/users/me/profile`, {}).subscribe();

    const req = httpMock.expectOne(`${baseApiUrl}/users/me/profile`);
    expect(req.request.headers.has('X-XSRF-TOKEN')).toBe(true);
    expect(req.request.headers.get('X-XSRF-TOKEN')).toBe(csrfToken);
    req.flush({});
  });

  it('should NOT add X-XSRF-TOKEN header to a GET request', () => {
    const csrfToken = 'test-csrf-token';
    setCookie('XSRF-TOKEN', csrfToken);

    httpClient.get(`${baseApiUrl}/users/me`).subscribe();

    const req = httpMock.expectOne(`${baseApiUrl}/users/me`);
    expect(req.request.headers.has('X-XSRF-TOKEN')).toBe(false);
    req.flush({});
  });

  it('should NOT add X-XSRF-TOKEN header to an auth request (e.g., login)', () => {
    const csrfToken = 'test-csrf-token';
    setCookie('XSRF-TOKEN', csrfToken);

    httpClient.post(`${baseApiUrl}/auth/login`, {}).subscribe();

    const req = httpMock.expectOne(`${baseApiUrl}/auth/login`);
    expect(req.request.headers.has('X-XSRF-TOKEN')).toBe(false);
    req.flush({});
  });

  it('should NOT add header if cookie is missing', () => {
    httpClient.post(`${baseApiUrl}/users/me/profile`, {}).subscribe();

    const req = httpMock.expectOne(`${baseApiUrl}/users/me/profile`);
    expect(req.request.headers.has('X-XSRF-TOKEN')).toBe(false);
    req.flush({});
  });
});
