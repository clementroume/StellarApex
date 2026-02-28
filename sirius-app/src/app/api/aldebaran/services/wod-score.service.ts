import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../../environments/environment';
import {
  ScalingLevel,
  ScoreComparisonResponse,
  WodScoreRequest,
  WodScoreResponse
} from '../models/score.model';
import {Slice} from '../../../core/models/pagination.model';


@Injectable({providedIn: 'root'})
export class WodScoreService {
  private readonly http = inject(HttpClient);

  getMyScores(wodId?: number, page: number = 0, size: number = 20): Observable<Slice<WodScoreResponse>> {
    let params = new HttpParams()
    .set('page', page)
    .set('size', size);

    if (wodId) {
      params = params.set('wodId', wodId);
    }

    return this.http.get<Slice<WodScoreResponse>>(this.buildUrl('/scores/me'), {params});
  }

  logScore(request: WodScoreRequest): Observable<WodScoreResponse> {
    return this.http.post<WodScoreResponse>(this.buildUrl('/scores'), request);
  }

  updateScore(id: number, request: WodScoreRequest): Observable<WodScoreResponse> {
    return this.http.put<WodScoreResponse>(this.buildUrl(`/scores/${id}`), request);
  }

  deleteScore(id: number): Observable<void> {
    return this.http.delete<void>(this.buildUrl(`/scores/${id}`));
  }

  compareScore(id: number): Observable<ScoreComparisonResponse> {
    return this.http.get<ScoreComparisonResponse>(this.buildUrl(`/scores/${id}/compare`));
  }

  getLeaderboard(
    wodId: number,
    scaling: ScalingLevel = 'RX',
    page: number = 0,
    size: number = 20
  ): Observable<Slice<WodScoreResponse>> {
    const params = new HttpParams()
    .set('scaling', scaling)
    .set('page', page)
    .set('size', size);

    return this.http.get<Slice<WodScoreResponse>>(this.buildUrl(`/scores/leaderboard/${wodId}`), {params});
  }

  private buildUrl(path: string): string {
    return `${environment.trainingUrl}${path}`;
  }
}
