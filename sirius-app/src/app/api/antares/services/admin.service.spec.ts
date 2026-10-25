import {TestBed} from '@angular/core/testing';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideHttpClient, withXhr} from '@angular/common/http';
import {AdminService} from './admin.service';
import {environment} from '../../../../environments/environment';

describe('AdminService', () => {
  let service: AdminService;
  let httpMock: HttpTestingController;
  const base = `${environment.authUrl}/admin`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AdminService, provideHttpClient(withXhr()), provideHttpClientTesting()],
    });
    service = TestBed.inject(AdminService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('searchUsers should GET /admin/users with pagination and filters', () => {
    service.searchUsers({search: 'sparta', gymId: 3, role: 'COACH', status: 'BANNED'}, 2, 20).subscribe();
    const req = httpMock.expectOne(`${base}/users?page=2&size=20&search=sparta&gymId=3&role=COACH&status=BANNED`);
    expect(req.request.method).toBe('GET');
    req.flush({content: []});
  });

  it('searchUsers should omit empty filters', () => {
    service.searchUsers({search: ''}).subscribe();
    httpMock.expectOne(`${base}/users?page=0&size=20`).flush({content: []});
  });

  it('lookupUsers should GET summaries by ids', () => {
    service.lookupUsers([4, 9]).subscribe();
    httpMock.expectOne(`${base}/users/lookup?ids=4,9`).flush([]);
  });

  it('getRecentSupportTargets should GET the recent targets', () => {
    service.getRecentSupportTargets().subscribe();
    httpMock.expectOne(`${base}/support-sessions/recent`).flush([]);
  });

  it('startSupportSession should POST the target and reason', () => {
    service.startSupportSession({userId: 4, reason: 'Bug report'}).subscribe();
    const req = httpMock.expectOne(`${base}/support-sessions`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({userId: 4, reason: 'Bug report'});
    req.flush({});
  });

  it('endSupportSession should DELETE the current session', () => {
    service.endSupportSession().subscribe();
    const req = httpMock.expectOne(`${base}/support-sessions/current`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null, {status: 204, statusText: 'No Content'});
  });
});
