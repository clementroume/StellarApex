import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../../environments/environment';
import {WodRequest, WodResponse, WodSummaryResponse, WodType} from '../models/wod.model';
import {Slice} from '../../../core/models/pagination.model';

@Injectable({providedIn: 'root'})
export class WodService {
  private readonly http = inject(HttpClient);

  getWods(
    search?: string,
    type?: WodType,
    movementId?: string,
    page: number = 0,
    size: number = 20
  ): Observable<Slice<WodSummaryResponse>> {
    let params = new HttpParams()
    .set('page', page)
    .set('size', size);

    if (search) params = params.set('search', search);
    if (type) params = params.set('type', type);
    if (movementId) params = params.set('movementId', movementId);

    return this.http.get<Slice<WodSummaryResponse>>(this.buildUrl('/wods'), {params});
  }

  getWod(id: number): Observable<WodResponse> {
    return this.http.get<WodResponse>(this.buildUrl(`/wods/${id}`));
  }

  createWod(request: WodRequest): Observable<WodResponse> {
    return this.http.post<WodResponse>(this.buildUrl('/wods'), request);
  }

  updateWod(id: number, request: WodRequest): Observable<WodResponse> {
    return this.http.put<WodResponse>(this.buildUrl(`/wods/${id}`), request);
  }

  deleteWod(id: number): Observable<void> {
    return this.http.delete<void>(this.buildUrl(`/wods/${id}`));
  }

  private buildUrl(path: string): string {
    return `${environment.trainingUrl}${path}`;
  }
}
