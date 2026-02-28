import {TestBed} from '@angular/core/testing';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideHttpClient} from '@angular/common/http';
import {GymService} from './gym.service';
import {environment} from '../../../../environments/environment';
import {
  GymRequest,
  GymResponse,
  GymSettingsRequest,
  GymStatus,
  JoinGymRequest
} from '../models/gym.model';
import {MembershipResponse} from '../models/membership.model';

describe('GymService', () => {
  let service: GymService;
  let httpMock: HttpTestingController;
  const base = environment.authUrl;

  const mockGymResponse: GymResponse = {
    isAutoSubscription: false,
    id: 1,
    name: 'Stellar Gym',
    description: 'A great place to train',
    isProgramming: true,
    status: 'ACTIVE',
    createdAt: new Date().toISOString()
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        GymService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(GymService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('createGym should POST to /gyms', () => {
    const payload: GymRequest = {name: 'New Gym', isProgramming: false, creationToken: 'token'};
    service.createGym(payload).subscribe(response => {
      expect(response).toEqual(mockGymResponse);
    });
    const req = httpMock.expectOne(`${base}/gyms`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush(mockGymResponse);
  });

  it('getGyms should GET from /gyms without params', () => {
    service.getGyms().subscribe(response => {
      expect(response).toEqual([mockGymResponse]);
    });
    const req = httpMock.expectOne(`${base}/gyms`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.has('status')).toBeFalse();
    req.flush([mockGymResponse]);
  });

  it('getGyms should GET from /gyms with status param', () => {
    const status: GymStatus = 'ACTIVE';
    service.getGyms(status).subscribe(response => {
      expect(response).toEqual([mockGymResponse]);
    });
    const req = httpMock.expectOne(`${base}/gyms?status=${status}`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('status')).toBe(status);
    req.flush([mockGymResponse]);
  });

  it('updateStatus should PUT to /gyms/{id}/status', () => {
    const gymId = 1;
    const status: GymStatus = 'SUSPENDED';
    service.updateStatus(gymId, status).subscribe(response => {
      expect(response).toEqual(mockGymResponse);
    });
    const req = httpMock.expectOne(`${base}/gyms/${gymId}/status?status=${status}`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({});
    req.flush(mockGymResponse);
  });

  it('joinGym should POST to /gyms/join', () => {
    const payload: JoinGymRequest = {gymId: 1, enrollmentCode: 'JOIN-ME'};
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
    service.joinGym(payload).subscribe(response => {
      expect(response).toEqual(mockMembershipResponse);
    });
    const req = httpMock.expectOne(`${base}/gyms/join`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush(mockMembershipResponse);
  });

  it('getSettings should GET from /gyms/{gymId}/settings', () => {
    const gymId = 1;
    const mockSettings: GymSettingsRequest = {enrollmentCode: 'CODE', isAutoSubscription: false};
    service.getSettings(gymId).subscribe(response => {
      expect(response).toEqual(mockSettings);
    });
    const req = httpMock.expectOne(`${base}/gyms/${gymId}/settings`);
    expect(req.request.method).toBe('GET');
    req.flush(mockSettings);
  });

  it('updateSettings should PUT to /gyms/{gymId}/settings', () => {
    const gymId = 1;
    const payload: GymSettingsRequest = {enrollmentCode: 'NEW-CODE', isAutoSubscription: true};
    service.updateSettings(gymId, payload).subscribe();
    const req = httpMock.expectOne(`${base}/gyms/${gymId}/settings`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(payload);
    req.flush(null);
  });

  it('deleteGym should DELETE to /gyms/{id}', () => {
    const gymId = 1;
    service.deleteGym(gymId).subscribe();
    const req = httpMock.expectOne(`${base}/gyms/${gymId}`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null, {status: 204, statusText: 'No Content'});
  });
});
