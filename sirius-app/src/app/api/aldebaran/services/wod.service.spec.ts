import {TestBed} from '@angular/core/testing';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {HttpContext, HttpContextToken, provideHttpClient, withXhr} from '@angular/common/http';
import {WodService} from './wod.service';
import {environment} from '../../../../environments/environment';
import {WodReferenceData, WodRequest, WodResponse, WodSummaryResponse} from '../models/wod.model';
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
    isPublic: true, competition: false,
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
    isPublic: true, competition: false,
    modalities: ['WEIGHTLIFTING', 'GYMNASTICS'],
    movements: [],
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  };

  const mockReferenceData: WodReferenceData = {
    wodTypes: ['AMRAP', 'FOR_TIME', 'EMOM'],
    structures: {},
    categories: ['GIRLS', 'HERO'],
    scoreTypes: ['TIME', 'REPS', 'LOAD'],
    unitGroups: {
      'WEIGHT': ['KG', 'LBS'],
      'DISTANCE': ['METERS', 'KILOMETERS']
    }
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [WodService, provideHttpClient(withXhr()), provideHttpClientTesting()],
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
    const type: string = 'FOR_TIME';
    const movementId = 42;
    service.getWods({search, type, movementId}).subscribe();

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
      isPublic: false, competition: false,
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
      isPublic: false, competition: false,
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

  it('getReferenceData should GET from /wods/reference-data', () => {
    service.getReferenceData().subscribe(response => {
      expect(response).toEqual(mockReferenceData);
    });

    const req = httpMock.expectOne(`${base}/wods/reference-data`);
    expect(req.request.method).toBe('GET');
    req.flush(mockReferenceData);
  });

  it('getWods should include the scope and gym filters', () => {
    service.getWods({scope: 'GYM', gymId: 5}).subscribe();

    const req = httpMock.expectOne(`${base}/wods?page=0&size=20&scope=GYM&gymId=5`);
    expect(req.request.method).toBe('GET');
    req.flush(mockSlice);
  });

  it('getWods should include the author filter', () => {
    service.getWods({authorId: 7}).subscribe();
    httpMock.expectOne(`${base}/wods?page=0&size=20&authorId=7`).flush(mockSlice);
  });

  it('getFacets should GET /wods/facets with the filters', () => {
    service.getFacets({search: 'fran', scope: 'GYM', gymId: 5}).subscribe(facets => {
      expect(facets.wodTypes).toEqual(['FOR_TIME']);
    });
    const req = httpMock.expectOne(`${base}/wods/facets?search=fran&scope=GYM&gymId=5`);
    expect(req.request.method).toBe('GET');
    req.flush({wodTypes: ['FOR_TIME'], gymIds: [5], authorIds: []});
  });

  it('getWods should forward the provided HttpContext', () => {
    const token = new HttpContextToken<boolean>(() => false);
    const context = new HttpContext().set(token, true);
    service.getWods({}, 2, 10, context).subscribe();

    const req = httpMock.expectOne(`${base}/wods?page=2&size=10`);
    expect(req.request.context.get(token)).toBe(true);
    req.flush(mockSlice);
  });

  it('notifyRefresh should emit on refreshNeeded$', () => {
    let emitted = 0;
    service.refreshNeeded$.subscribe(() => emitted++);
    service.notifyRefresh();
    expect(emitted).toBe(1);
  });

  it('copyWod should POST the target to /wods/{id}/copy', () => {
    service.copyWod(1, {gymId: 5, competition: true}).subscribe();
    const req = httpMock.expectOne(`${base}/wods/1/copy`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({gymId: 5, competition: true});
    req.flush(mockWodResponse);
  });
});
