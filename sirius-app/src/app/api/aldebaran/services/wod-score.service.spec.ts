import {TestBed} from '@angular/core/testing';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideHttpClient} from '@angular/common/http';
import {WodScoreService} from './wod-score.service';
import {environment} from '../../../../environments/environment';
import {
  ScalingLevel,
  ScoreComparisonResponse,
  WodScoreRequest,
  WodScoreResponse
} from '../models/score.model';
import {Slice} from '../../../core/models/pagination.model';


describe('WodScoreService', () => {
  let service: WodScoreService;
  let httpMock: HttpTestingController;
  const base = environment.trainingUrl;

  const mockScoreResponse: WodScoreResponse = {
    id: 1,
    userId: 1,
    date: new Date().toISOString().split('T')[0],
    wodSummary: {id: 1, title: 'Fran', wodType: 'FOR_TIME', scoreType: 'TIME', createdAt: ''},
    timeSeconds: 180,
    scaling: 'RX',
    personalRecord: true,
    timeCapped: false,
  };

  const mockSlice: Slice<WodScoreResponse> = {
    content: [mockScoreResponse],
    last: true,
    first: true,
    size: 1,
    number: 0,
    numberOfElements: 1,
    empty: false,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [WodScoreService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(WodScoreService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('getMyScores should GET from /scores/me', () => {
    service.getMyScores().subscribe();
    const req = httpMock.expectOne(`${base}/scores/me?page=0&size=20`);
    expect(req.request.method).toBe('GET');
    req.flush(mockSlice);
  });

  it('getMyScores should include wodId param if provided', () => {
    const wodId = 123;
    service.getMyScores(wodId).subscribe();
    const req = httpMock.expectOne(`${base}/scores/me?page=0&size=20&wodId=${wodId}`);
    expect(req.request.method).toBe('GET');
    req.flush(mockSlice);
  });

  it('logScore should POST to /scores', () => {
    const payload: WodScoreRequest = {
      wodId: 1,
      date: '2023-01-01',
      scaling: 'RX',
      timeCapped: false
    };
    service.logScore(payload).subscribe();
    const req = httpMock.expectOne(`${base}/scores`);
    expect(req.request.method).toBe('POST');
    req.flush(mockScoreResponse);
  });

  it('updateScore should PUT to /scores/{id}', () => {
    const scoreId = 1;
    const payload: WodScoreRequest = {
      wodId: 1,
      date: '2023-01-01',
      scaling: 'SCALED',
      timeCapped: false
    };
    service.updateScore(scoreId, payload).subscribe();
    const req = httpMock.expectOne(`${base}/scores/${scoreId}`);
    expect(req.request.method).toBe('PUT');
    req.flush(mockScoreResponse);
  });

  it('deleteScore should DELETE to /scores/{id}', () => {
    const scoreId = 1;
    service.deleteScore(scoreId).subscribe();
    const req = httpMock.expectOne(`${base}/scores/${scoreId}`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null, {status: 204, statusText: 'No Content'});
  });

  it('compareScore should GET from /scores/{id}/compare', () => {
    const scoreId = 1;
    const mockComparison: ScoreComparisonResponse = {rank: 10, totalScores: 100, percentile: 90};
    service.compareScore(scoreId).subscribe(res => {
      expect(res).toEqual(mockComparison);
    });
    const req = httpMock.expectOne(`${base}/scores/${scoreId}/compare`);
    expect(req.request.method).toBe('GET');
    req.flush(mockComparison);
  });

  it('getLeaderboard should GET from /scores/leaderboard/{id}', () => {
    const wodId = 1;
    const scaling: ScalingLevel = 'RX';
    service.getLeaderboard(wodId, scaling).subscribe();
    const req = httpMock.expectOne(`${base}/scores/leaderboard/${wodId}?scaling=${scaling}&page=0&size=20`);
    expect(req.request.method).toBe('GET');
    req.flush(mockSlice);
  });
});
