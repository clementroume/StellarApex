import {TestBed} from '@angular/core/testing';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideHttpClient, withXhr} from '@angular/common/http';
import {SessionService} from './session.service';
import {environment} from '../../../../environments/environment';
import {SessionRequest} from '../models/session.model';

describe('SessionService', () => {
  let service: SessionService;
  let httpMock: HttpTestingController;
  const base = environment.trainingUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({providers: [provideHttpClient(withXhr()), provideHttpClientTesting()]});
    service = TestBed.inject(SessionService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should list sessions with the given filters only', () => {
    service.getSessions({scope: 'GYM', gymId: 4, from: '2026-11-02', to: '2026-11-08'}).subscribe();
    const req = httpMock.expectOne(r => r.url === `${base}/sessions`);
    expect(req.request.params.get('scope')).toBe('GYM');
    expect(req.request.params.get('gymId')).toBe('4');
    expect(req.request.params.get('from')).toBe('2026-11-02');
    expect(req.request.params.get('to')).toBe('2026-11-08');
    expect(req.request.params.has('type')).toBe(false);
    req.flush({content: []});
  });

  it('should create, update, change the visibility and delete a session', () => {
    const request = {type: 'WOD', discipline: 'CROSSFIT', date: '2026-11-03', blocks: []} as SessionRequest;
    service.createSession(request).subscribe();
    expect(httpMock.expectOne({method: 'POST', url: `${base}/sessions`}).request.body).toEqual(request);
    service.updateSession(5, request).subscribe();
    httpMock.expectOne({method: 'PUT', url: `${base}/sessions/5`}).flush({});
    service.updateVisibility(5, {visibilityPolicy: 'DAY_OF', contentVisibleAt: '2026-11-03T18:00'}).subscribe();
    expect(httpMock.expectOne({method: 'PUT', url: `${base}/sessions/5/visibility`}).request.body.visibilityPolicy).toBe('DAY_OF');
    service.deleteSession(5).subscribe();
    httpMock.expectOne({method: 'DELETE', url: `${base}/sessions/5`}).flush(null);
  });

  it('should log a session and manage the journal', () => {
    service.logSession(5, '2026-11-03').subscribe();
    expect(httpMock.expectOne({method: 'POST', url: `${base}/sessions/5/logs`}).request.body).toEqual({date: '2026-11-03', gymId: null});
    service.setBlockDone(9, 2, false).subscribe();
    expect(httpMock.expectOne({method: 'PUT', url: `${base}/session-logs/9/blocks/2`}).request.body).toEqual({done: false});
    service.deleteLog(9).subscribe();
    httpMock.expectOne({method: 'DELETE', url: `${base}/session-logs/9`}).flush(null);
    service.getMyLogs('2026-11-01').subscribe();
    expect(httpMock.expectOne(r => r.url === `${base}/session-logs/me`).request.params.get('from')).toBe('2026-11-01');
  });

  it('should read and save the session settings of a gym', () => {
    service.getSettings(4).subscribe();
    httpMock.expectOne({method: 'GET', url: `${base}/gyms/4/session-settings`}).flush({});
    service.updateSettings(4, {defaultVisibility: 'ADVANCE', timeZone: 'Europe/Paris'}).subscribe();
    httpMock.expectOne({method: 'PUT', url: `${base}/gyms/4/session-settings`}).flush({});
  });
});
