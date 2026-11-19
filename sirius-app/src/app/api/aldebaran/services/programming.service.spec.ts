import {TestBed} from '@angular/core/testing';
import {provideHttpClient, withXhr} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';
import {ProgrammingService} from './programming.service';
import {environment} from '../../../../environments/environment';

describe('ProgrammingService', () => {
  let service: ProgrammingService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({providers: [provideHttpClient(withXhr()), provideHttpClientTesting()]});
    service = TestBed.inject(ProgrammingService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('asks the balance of a cycle with its switches and the profile days', () => {
    service.getBalance(12, {start: '2026-11-16', weeks: 5, scaled: true, optional: false, days: ['MONDAY', 'FRIDAY']})
      .subscribe();
    const req = http.expectOne(r => r.url === `${environment.trainingUrl}/programming/gyms/12/balance`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('start')).toBe('2026-11-16');
    expect(req.request.params.get('weeks')).toBe('5');
    expect(req.request.params.get('scaled')).toBe('true');
    expect(req.request.params.get('optional')).toBe('false');
    expect(req.request.params.getAll('days')).toEqual(['MONDAY', 'FRIDAY']);
    req.flush({});
  });

  it('sends no days for the full week only', () => {
    service.getBalance(12, {start: '2026-11-16', weeks: 4, scaled: false, optional: false, days: []}).subscribe();
    const req = http.expectOne(r => r.url.endsWith('/programming/gyms/12/balance'));
    expect(req.request.params.has('days')).toBe(false);
    req.flush({});
  });
});
