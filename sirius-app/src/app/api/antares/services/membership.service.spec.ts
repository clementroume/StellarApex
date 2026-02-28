import {TestBed} from '@angular/core/testing';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideHttpClient} from '@angular/common/http';
import {MembershipService} from './membership.service';
import {environment} from '../../../../environments/environment';
import {
  MembershipResponse,
  MembershipStatus,
  MembershipUpdateRequest
} from '../models/membership.model';

describe('MembershipService', () => {
  let service: MembershipService;
  let httpMock: HttpTestingController;
  const base = environment.authUrl;

  const mockMembershipResponse: MembershipResponse = {
    id: 1,
    user: {
      id: 101,
      firstName: 'John',
      lastName: 'Doe',
      email: 'john.doe@example.com',
      platformRole: 'USER' // ou 'ADMIN'
    },
    gymRole: 'ATHLETE', // ou 'COACH' / 'OWNER'
    status: 'PENDING',
    permissions: []
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        MembershipService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(MembershipService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('getMemberships should GET from /memberships with gymId', () => {
    const gymId = 1;
    service.getMemberships(gymId).subscribe(response => {
      expect(response).toEqual([mockMembershipResponse]);
    });
    const req = httpMock.expectOne(`${base}/memberships?gymId=${gymId}`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('gymId')).toBe(String(gymId));
    req.flush([mockMembershipResponse]);
  });

  it('getMemberships should GET from /memberships with gymId and status', () => {
    const gymId = 1;
    const status: MembershipStatus = 'PENDING';
    service.getMemberships(gymId, status).subscribe(response => {
      expect(response).toEqual([mockMembershipResponse]);
    });
    const req = httpMock.expectOne(`${base}/memberships?gymId=${gymId}&status=${status}`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('gymId')).toBe(String(gymId));
    expect(req.request.params.get('status')).toBe(status);
    req.flush([mockMembershipResponse]);
  });

  it('updateMembership should PUT to /memberships/{id}', () => {
    const membershipId = 1;
    const payload: MembershipUpdateRequest = {
      status: 'ACTIVE',
      gymRole: 'COACH',
      permissions: ['MANAGE_MEMBERSHIPS']
    };
    service.updateMembership(membershipId, payload).subscribe(response => {
      expect(response).toEqual(mockMembershipResponse);
    });
    const req = httpMock.expectOne(`${base}/memberships/${membershipId}`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(payload);
    req.flush(mockMembershipResponse);
  });

  it('deleteMembership should DELETE to /memberships/{id}', () => {
    const membershipId = 1;
    service.deleteMembership(membershipId).subscribe();
    const req = httpMock.expectOne(`${base}/memberships/${membershipId}`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null, {status: 204, statusText: 'No Content'});
  });
});
