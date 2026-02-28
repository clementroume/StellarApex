import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../../environments/environment';
import {
  Category,
  MovementRequest,
  MovementResponse,
  MovementSummaryResponse
} from '../models/movement.model';

@Injectable({providedIn: 'root'})
export class MovementService {
  private readonly http = inject(HttpClient);

  searchMovements(query: string = ''): Observable<MovementSummaryResponse[]> {
    const params = new HttpParams().set('query', query);
    return this.http.get<MovementSummaryResponse[]>(this.buildUrl('/movements'), {params});
  }

  getMovement(id: string): Observable<MovementResponse> {
    return this.http.get<MovementResponse>(this.buildUrl(`/movements/${id}`));
  }

  getMovementsByCategory(category: Category): Observable<MovementSummaryResponse[]> {
    return this.http.get<MovementSummaryResponse[]>(this.buildUrl(`/movements/category/${category}`));
  }

  createMovement(request: MovementRequest): Observable<MovementResponse> {
    return this.http.post<MovementResponse>(this.buildUrl('/movements'), request);
  }

  updateMovement(id: string, request: MovementRequest): Observable<MovementResponse> {
    return this.http.put<MovementResponse>(this.buildUrl(`/movements/${id}`), request);
  }

  private buildUrl(path: string): string {
    return `${environment.trainingUrl}${path}`;
  }
}
