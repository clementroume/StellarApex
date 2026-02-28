import {TestBed} from '@angular/core/testing';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideHttpClient} from '@angular/common/http';
import {MuscleService} from './muscle.service';
import {environment} from '../../../../environments/environment';
import {MuscleGroup, MuscleRequest, MuscleResponse} from '../models/muscle.model';

describe('MuscleService', () => {
  let service: MuscleService;
  let httpMock: HttpTestingController;
  const base = environment.trainingUrl;

  const mockMuscleResponse: MuscleResponse = {
    id: 1,
    medicalName: 'Pectoralis Major',
    commonNameEn: 'Chest',
    muscleGroup: 'CHEST',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [MuscleService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(MuscleService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('getMuscles should GET from /muscles', () => {
    service.getMuscles().subscribe(response => {
      expect(response).toEqual([mockMuscleResponse]);
    });
    const req = httpMock.expectOne(`${base}/muscles`);
    expect(req.request.method).toBe('GET');
    req.flush([mockMuscleResponse]);
  });

  it('getMuscles should include group param if provided', () => {
    const group: MuscleGroup = 'CHEST';
    service.getMuscles(group).subscribe();
    const req = httpMock.expectOne(`${base}/muscles?group=${group}`);
    expect(req.request.method).toBe('GET');
    req.flush([mockMuscleResponse]);
  });

  it('getMuscle should GET from /muscles/{medicalName}', () => {
    const medicalName = 'Pectoralis Major';
    service.getMuscle(medicalName).subscribe(response => {
      expect(response).toEqual(mockMuscleResponse);
    });
    const req = httpMock.expectOne(`${base}/muscles/${medicalName}`);
    expect(req.request.method).toBe('GET');
    req.flush(mockMuscleResponse);
  });

  it('createMuscle should POST to /muscles', () => {
    const payload: MuscleRequest = {medicalName: 'Deltoid', muscleGroup: 'SHOULDERS'};
    service.createMuscle(payload).subscribe();
    const req = httpMock.expectOne(`${base}/muscles`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush(mockMuscleResponse);
  });

  it('updateMuscle should PUT to /muscles/{id}', () => {
    const muscleId = 1;
    const payload: MuscleRequest = {medicalName: 'Pectoralis Major', muscleGroup: 'CHEST'};
    service.updateMuscle(muscleId, payload).subscribe();
    const req = httpMock.expectOne(`${base}/muscles/${muscleId}`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(payload);
    req.flush(mockMuscleResponse);
  });
});
