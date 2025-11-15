import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { loadingInterceptor } from './loading.interceptor';
import { LoadingService } from '../services/loading.service';

describe('loadingInterceptor', () => {
  let httpMock: HttpTestingController;
  let httpClient: HttpClient;
  let loadingService: LoadingService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([loadingInterceptor])),
        provideHttpClientTesting(),
        LoadingService,
      ],
    });

    httpMock = TestBed.inject(HttpTestingController);
    httpClient = TestBed.inject(HttpClient);
    loadingService = TestBed.inject(LoadingService);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should call show() and hide() on a successful request', () => {
    const showSpy = spyOn(loadingService, 'show').and.callThrough();
    const hideSpy = spyOn(loadingService, 'hide').and.callThrough();

    httpClient.get('/api/test').subscribe();

    expect(showSpy).toHaveBeenCalledTimes(1);

    const req = httpMock.expectOne('/api/test');
    req.flush({});

    expect(hideSpy).toHaveBeenCalledTimes(1);
  });

  it('should call show() and hide() on a failed request', () => {
    const showSpy = spyOn(loadingService, 'show').and.callThrough();
    const hideSpy = spyOn(loadingService, 'hide').and.callThrough();

    httpClient.get('/api/test').subscribe({
      error: () => {} // Catch the error to prevent it from failing the test
    });

    expect(showSpy).toHaveBeenCalledTimes(1);

    const req = httpMock.expectOne('/api/test');
    req.flush({}, { status: 500, statusText: 'Server Error' });

    expect(hideSpy).toHaveBeenCalledTimes(1);
  });
});
