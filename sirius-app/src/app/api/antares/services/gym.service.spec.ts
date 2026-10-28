import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { GymService } from './gym.service';
import { environment } from '../../../../environments/environment';
import {
    GymRequest,
    GymResponse,
    GymSettingsRequest,
    GymSettingsResponse,
    GymStatus,
    JoinGymRequest
} from '../models/gym.model';
import { MembershipSummary } from '../models/membership.model';

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
                provideHttpClient(withXhr()),
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
        const payload: GymRequest = { name: 'New Gym', isProgramming: false };
        service.createGym(payload).subscribe(response => {
            expect(response).toEqual(mockGymResponse);
        });
        const req = httpMock.expectOne(`${base}/gyms`);
        expect(req.request.method).toBe('POST');
        expect(req.request.body).toEqual(payload);
        req.flush(mockGymResponse);
    });

    it('searchGyms should GET /gyms with paging, and search and type when given', () => {
        service.searchGyms().subscribe();
        const all = httpMock.expectOne(r => r.url === `${base}/gyms`);
        expect(all.request.params.get('page')).toBe('0');
        expect(all.request.params.has('search')).toBe(false);
        expect(all.request.params.has('type')).toBe(false);
        all.flush({content: [], last: true});

        service.searchGyms('sparta', 'BOX', 2, 10).subscribe();
        const filtered = httpMock.expectOne(r => r.url === `${base}/gyms`);
        expect(filtered.request.params.get('search')).toBe('sparta');
        expect(filtered.request.params.get('type')).toBe('BOX');
        expect(filtered.request.params.get('page')).toBe('2');
        expect(filtered.request.params.get('size')).toBe('10');
        filtered.flush({content: [], last: true});
    });

    it('getGym should GET /gyms/{id}', () => {
        service.getGym(3).subscribe();
        expect(httpMock.expectOne(`${base}/gyms/3`).request.method).toBe('GET');
    });

    it('updateGym should PUT /gyms/{id}', () => {
        service.updateGym(3, {name: 'Renamed'}).subscribe();
        const req = httpMock.expectOne(`${base}/gyms/3`);
        expect(req.request.method).toBe('PUT');
        expect(req.request.body).toEqual({name: 'Renamed'});
    });

    it('leaveGym should DELETE /gyms/{id}/membership/me', () => {
        service.leaveGym(3).subscribe();
        expect(httpMock.expectOne(`${base}/gyms/3/membership/me`).request.method).toBe('DELETE');
    });

    it('regenerateEnrollmentCode should POST /gyms/{id}/settings/enrollment-code', () => {
        service.regenerateEnrollmentCode(3).subscribe();
        expect(httpMock.expectOne(`${base}/gyms/3/settings/enrollment-code`).request.method).toBe('POST');
    });

    it('transferOwnership should POST the new holder membership', () => {
        service.transferOwnership(3, 50).subscribe();
        const req = httpMock.expectOne(`${base}/gyms/3/transfer-ownership`);
        expect(req.request.method).toBe('POST');
        expect(req.request.body).toEqual({membershipId: 50});
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
        const payload: JoinGymRequest = { gymId: 1, enrollmentCode: 'JOIN-ME' };
        const membership: MembershipSummary = {
            gymId: 1, gymName: 'Stellar Gym', gymStatus: 'ACTIVE', gymRole: 'ATHLETE', status: 'PENDING',
            permissions: [], gymProgramming: false, usable: false
        };
        service.joinGym(payload).subscribe(response => {
            expect(response).toEqual(membership);
        });
        const req = httpMock.expectOne(`${base}/gyms/join`);
        expect(req.request.method).toBe('POST');
        expect(req.request.body).toEqual(payload);
        req.flush(membership);
    });

    it('getSettings should GET from /gyms/{gymId}/settings', () => {
        const gymId = 1;
        const mockSettings: GymSettingsResponse = {
            id: 1, name: 'Stellar Gym', isProgramming: false, status: 'ACTIVE', enrollmentCode: 'CODE', isAutoSubscription: false
        };
        service.getSettings(gymId).subscribe(response => {
            expect(response).toEqual(mockSettings);
        });
        const req = httpMock.expectOne(`${base}/gyms/${gymId}/settings`);
        expect(req.request.method).toBe('GET');
        req.flush(mockSettings);
    });

    it('updateSettings should PUT to /gyms/{gymId}/settings', () => {
        const gymId = 1;
        const payload: GymSettingsRequest = { enrollmentCode: 'NEW-CODE', isAutoSubscription: true };
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
        req.flush(null, { status: 204, statusText: 'No Content' });
    });

    it('getAthletes should GET /gyms/{gymId}/athletes', () => {
        service.getAthletes(1).subscribe(athletes => expect(athletes).toEqual([{id: 9, displayName: 'Anne L.'}]));
        const req = httpMock.expectOne(`${base}/gyms/1/athletes`);
        expect(req.request.method).toBe('GET');
        req.flush([{id: 9, displayName: 'Anne L.'}]);
    });
});
