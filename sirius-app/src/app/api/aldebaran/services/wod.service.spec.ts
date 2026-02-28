import {TestBed} from '@angular/core/testing';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideHttpClient} from '@angular/common/http';
import {WodService} from './wod.service';
import {environment} from '../../../../environments/environment';
import {WodRequest, WodResponse, WodSummaryResponse, WodType} from '../models/wod.model';
import {Slice} from '../../../core/models/pagination.model';

describe('WodService', () => {
  let service: WodService;
  let httpMock: HttpTestingController;
  const base = environment.trainingUrl;

  const mockWodSummary: WodSummaryResponse = {
    id: 1,
    title: 'Fran',
    wodType: 'FOR_TIME',
    scoreType: 'TIME',
    createdAt: new Date().toISOString(),
  };

  const mockSlice: Slice<WodSummaryResponse> = {
    content: [mockWodSummary],
    last: true,
    first: true,
    size: 1,
    number: 0,
    numberOfElements: 1,
    empty: false,
  };

  const mockWodResponse: WodResponse = {
    id: 1,
    title: 'Fran',
    wodType: 'FOR_TIME',
    scoreType: 'TIME',
    isPublic: true,
    modalities: ['WEIGHTLIFTING', 'GYMNASTICS'],
    movements: [],
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [WodService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(WodService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('getWods should GET from /wods with pagination', () => {
    service.getWods().subscribe(response => {
      expect(response).toEqual(mockSlice);
    });
    const req = httpMock.expectOne(`${base}/wods?page=0&size=20`);
    expect(req.request.method).toBe('GET');
    req.flush(mockSlice);
  });

  it('getWods should include optional filters', () => {
    const search = 'Fran';
    const type: WodType = 'FOR_TIME';
    const movementId = 'WL-SQ-001';
    service.getWods(search, type, movementId).subscribe();

    const req = httpMock.expectOne(`${base}/wods?page=0&size=20&search=${search}&type=${type}&movementId=${movementId}`);
    expect(req.request.method).toBe('GET');
    req.flush(mockSlice);
  });

  it('getWod should GET from /wods/{id}', () => {
    const wodId = 1;
    service.getWod(wodId).subscribe(response => {
      expect(response).toEqual(mockWodResponse);
    });
    const req = httpMock.expectOne(`${base}/wods/${wodId}`);
    expect(req.request.method).toBe('GET');
    req.flush(mockWodResponse);
  });

  it('createWod should POST to /wods', () => {
    const payload: WodRequest = {
      title: 'New WOD',
      wodType: 'AMRAP',
      scoreType: 'REPS',
      isPublic: false,
      movements: []
    };
    service.createWod(payload).subscribe();
    const req = httpMock.expectOne(`${base}/wods`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush(mockWodResponse);
  });

  it('updateWod should PUT to /wods/{id}', () => {
    const wodId = 1;
    const payload: WodRequest = {
      title: 'Updated WOD',
      wodType: 'AMRAP',
      scoreType: 'REPS',
      isPublic: false,
      movements: []
    };
    service.updateWod(wodId, payload).subscribe();
    const req = httpMock.expectOne(`${base}/wods/${wodId}`);
    expect(req.request.method).toBe('PUT');
    req.flush(mockWodResponse);
  });

  it('deleteWod should DELETE to /wods/{id}', () => {
    const wodId = 1;
    service.deleteWod(wodId).subscribe();
    const req = httpMock.expectOne(`${base}/wods/${wodId}`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null, {status: 204, statusText: 'No Content'});
  });
});
