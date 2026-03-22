import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpContext, HttpParams} from '@angular/common/http';
import {Observable, Subject} from 'rxjs';
import {environment} from '../../../../environments/environment';
import {
  MovementReferenceData,
  MovementRequest,
  MovementResponse,
  MovementSummaryResponse
} from '../models/movement.model';

@Injectable({providedIn: 'root'})
export class MovementService {
  private readonly http = inject(HttpClient);
  public refreshNeeded$ = new Subject<void>();

  searchMovements(query: string = '', context?: HttpContext): Observable<MovementSummaryResponse[]> {
    const params = new HttpParams().set('query', query);
    return this.http.get<MovementSummaryResponse[]>(this.buildUrl(), {params, context});
  }

  getMovement(id: number): Observable<MovementResponse> {
    return this.http.get<MovementResponse>(this.buildUrl(`/${id}`));
  }

  createMovement(request: MovementRequest): Observable<MovementResponse> {
    return this.http.post<MovementResponse>(this.buildUrl(), request);
  }

  updateMovement(id: number, request: MovementRequest): Observable<MovementResponse> {
    return this.http.put<MovementResponse>(this.buildUrl(`/${id}`), request);
  }

  deleteMovement(id: number): Observable<void> {
    return this.http.delete<void>(this.buildUrl(`/${id}`));
  }

  getReferenceData(): Observable<MovementReferenceData> {
    return this.http.get<MovementReferenceData>(this.buildUrl(`/reference-data`));
  }

  private buildUrl(path: string = ''): string {
    return `${environment.trainingUrl}/movements${path}`;
  }

  notifyRefresh() {
    this.refreshNeeded$.next();
  }
}
