import {TestBed} from '@angular/core/testing';
import {LoadingService} from './loading.service';

describe('LoadingService', () => {
  let service: LoadingService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [LoadingService]
    });
    service = TestBed.inject(LoadingService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('isLoading should be false initially', () => {
    expect(service.isLoading()).toBe(false);
  });

  it('should set isLoading to true after one show() call', () => {
    service.show();
    expect(service.isLoading()).toBe(true);
  });

  it('should set isLoading to false after one show() and one hide() call', () => {
    service.show();
    service.hide();
    expect(service.isLoading()).toBe(false);
  });

  it('should remain loading if hide() is called fewer times than show()', () => {
    service.show();
    service.show();
    service.hide();
    expect(service.isLoading()).toBe(true);
  });

  it('should stop loading only when all requests are hidden', () => {
    service.show();
    service.show();
    service.hide();
    service.hide();
    expect(service.isLoading()).toBe(false);
  });

  it('should not go below zero', () => {
    service.hide();
    service.hide();
    expect((service as any)._activeRequests()).toBe(0);
    expect(service.isLoading()).toBe(false);
  });
});
