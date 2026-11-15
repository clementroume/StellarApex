import {TestBed} from '@angular/core/testing';
import {provideHttpClient, withXhr} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {environment} from '../../../../environments/environment';
import {BalanceService} from './balance.service';

describe('BalanceService', () => {
  let service: BalanceService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({providers: [provideHttpClient(withXhr()), provideHttpClientTesting()]});
    service = TestBed.inject(BalanceService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('asks the balance of a week by its Monday', () => {
    service.getMyBalance('2026-11-09').subscribe();
    const req = http.expectOne(`${environment.trainingUrl}/balance/me?week=2026-11-09`);
    expect(req.request.method).toBe('GET');
    req.flush({});
  });
});
