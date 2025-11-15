import { TestBed, fakeAsync, flush } from '@angular/core/testing';
import { LoadingService } from './loading.service';

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
    expect(service.isLoading()).toBeFalse();
  });

  it('should set isLoading to true after one show() call', fakeAsync(() => {
    service.show();
    flush(); // Flush the signal update
    expect(service.isLoading()).toBeTrue();
  }));

  it('should set isLoading to false after one show() and one hide() call', fakeAsync(() => {
    service.show();
    service.hide();
    flush(); // Flush the signal updates
    expect(service.isLoading()).toBeFalse();
  }));

  it('should remain loading if hide() is called fewer times than show()', fakeAsync(() => {
    service.show();
    service.show();
    service.hide();
    flush(); // Flush the signal updates
    expect(service.isLoading()).toBeTrue();
  }));

  it('should stop loading only when all requests are hidden', fakeAsync(() => {
    service.show();
    service.show();
    service.hide();
    service.hide();
    flush(); // Flush the signal updates
    expect(service.isLoading()).toBeFalse();
  }));

  it('should not go below zero', fakeAsync(() => {
    service.hide();
    service.hide();
    flush(); // Flush the signal updates
    expect((service as any)._activeRequests()).toBe(0);
    expect(service.isLoading()).toBeFalse();
  }));
});
