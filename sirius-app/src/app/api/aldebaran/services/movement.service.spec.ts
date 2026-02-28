import {TestBed} from '@angular/core/testing';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideHttpClient} from '@angular/common/http';
import {MovementService} from './movement.service';
import {environment} from '../../../../environments/environment';
import {
  Category,
  MovementRequest,
  MovementResponse,
  MovementSummaryResponse
} from '../models/movement.model';

describe('MovementService', () => {
  let service: MovementService;
  let httpMock: HttpTestingController;
  const base = environment.trainingUrl;

  const mockMovementSummary: MovementSummaryResponse = {
    id: 'WL-SQ-001',
    name: 'Back Squat',
    category: 'SQUAT',
  };

  const mockMovementResponse: MovementResponse = {
    bodyweightFactor: 0,
    id: 'WL-SQ-001',
    name: 'Back Squat',
    category: 'SQUAT',
    equipment: ['BARBELL'],
    techniques: [],
    targetedMuscles: [],
    involvesBodyweight: false,
    loadBearing: true
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [MovementService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(MovementService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('searchMovements should GET from /movements with a query', () => {
    const query = 'squat';
    service.searchMovements(query).subscribe();
    const req = httpMock.expectOne(`${base}/movements?query=${query}`);
    expect(req.request.method).toBe('GET');
    req.flush([mockMovementSummary]);
  });

  it('getMovement should GET from /movements/{id}', () => {
    const movementId = 'WL-SQ-001';
    service.getMovement(movementId).subscribe();
    const req = httpMock.expectOne(`${base}/movements/${movementId}`);
    expect(req.request.method).toBe('GET');
    req.flush(mockMovementResponse);
  });

  it('getMovementsByCategory should GET from /movements/category/{category}', () => {
    const category: Category = 'SQUAT';
    service.getMovementsByCategory(category).subscribe();
    const req = httpMock.expectOne(`${base}/movements/category/${category}`);
    expect(req.request.method).toBe('GET');
    req.flush([mockMovementSummary]);
  });

  it('createMovement should POST to /movements', () => {
    const payload: MovementRequest = {
      equipment: [], techniques: [],
      name: 'Front Squat',
      category: 'SQUAT',
      involvesBodyweight: false
    };
    service.createMovement(payload).subscribe();
    const req = httpMock.expectOne(`${base}/movements`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush(mockMovementResponse);
  });

  it('updateMovement should PUT to /movements/{id}', () => {
    const movementId = 'WL-SQ-001';
    const payload: MovementRequest = {
      equipment: [], techniques: [],
      name: 'High-Bar Back Squat',
      category: 'SQUAT',
      involvesBodyweight: false
    };
    service.updateMovement(movementId, payload).subscribe();
    const req = httpMock.expectOne(`${base}/movements/${movementId}`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(payload);
    req.flush(mockMovementResponse);
  });
});
