import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpContext, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../../environments/environment';
import {ScoreComparisonResponse, ScoreReferenceData, ScoreRequest, ScoreResponse} from '../models/score.model';
import {Slice} from '../../../core/models/pagination.model';
import {Division} from '../../antares/models/user.model';


@Injectable({providedIn: 'root'})
export class ScoreService {
  private readonly http = inject(HttpClient);

  getMyScores(wodId?: number, page: number = 0, size: number = 20, context?: HttpContext): Observable<Slice<ScoreResponse>> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size);

    if (wodId) {
      params = params.set('wodId', wodId);
    }

    return this.http.get<Slice<ScoreResponse>>(this.buildUrl('/me'), {params, context});
  }

  logScore(request: ScoreRequest): Observable<ScoreResponse> {
    return this.http.post<ScoreResponse>(this.buildUrl(), request);
  }

  updateScore(id: number, request: ScoreRequest): Observable<ScoreResponse> {
    return this.http.put<ScoreResponse>(this.buildUrl(`/${id}`), request);
  }

  deleteScore(id: number): Observable<void> {
    return this.http.delete<void>(this.buildUrl(`/${id}`));
  }

  verifyScore(id: number): Observable<ScoreResponse> {
    return this.http.post<ScoreResponse>(this.buildUrl(`/${id}/verify`), {});
  }

  unverifyScore(id: number): Observable<ScoreResponse> {
    return this.http.delete<ScoreResponse>(this.buildUrl(`/${id}/verify`));
  }

  compareScore(id: number): Observable<ScoreComparisonResponse> {
    return this.http.get<ScoreComparisonResponse>(this.buildUrl(`/${id}/compare`));
  }

  getLeaderboard(
    wodId: number,
    scaling: string = 'RX',
    page: number = 0,
    size: number = 20,
    gymIds: number[] = [],
    verifiedOnly: boolean = false,
    division: Division | null = null
  ): Observable<Slice<ScoreResponse>> {
    let params = new HttpParams()
      .set('scaling', scaling)
      .set('page', page)
      .set('size', size);
    // Scores performed in some of the user's boxes / programs (narrow leaderboard), else worldwide
    if (gymIds.length > 0) {
      params = params.set('gymIds', gymIds.join(','));
    }
    if (verifiedOnly) {
      params = params.set('verifiedOnly', true);
    }
    // Scores logged in this division only, else every score
    if (division) {
      params = params.set('division', division);
    }

    return this.http.get<Slice<ScoreResponse>>(this.buildUrl(`/leaderboard/${wodId}`), {params});
  }

  getReferenceData(): Observable<ScoreReferenceData> {
    return this.http.get<ScoreReferenceData>(this.buildUrl('/reference-data'));
  }

  private buildUrl(path: string = ''): string {
    return `${environment.trainingUrl}/scores${path}`;
  }
}
