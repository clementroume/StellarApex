import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../../environments/environment';
import {MuscleReferenceData, MuscleRequest, MuscleResponse} from '../models/muscle.model';

@Injectable({providedIn: 'root'})
export class MuscleService {
  private readonly http = inject(HttpClient);

  getMuscles(group?: string): Observable<MuscleResponse[]> {
    let params = new HttpParams();
    if (group) {
      params = params.set('group', group);
    }
    return this.http.get<MuscleResponse[]>(this.buildUrl('/muscles'), {params});
  }

  getMuscle(id: number): Observable<MuscleResponse> {
    return this.http.get<MuscleResponse>(this.buildUrl(`/muscles/${id}`));
  }

  createMuscle(request: MuscleRequest): Observable<MuscleResponse> {
    return this.http.post<MuscleResponse>(this.buildUrl('/muscles'), request);
  }

  updateMuscle(id: number, request: MuscleRequest): Observable<MuscleResponse> {
    // Note: Update uses the technical ID (Long), while Get uses the medical name (String)
    return this.http.put<MuscleResponse>(this.buildUrl(`/muscles/${id}`), request);
  }

  getReferenceData(): Observable<MuscleReferenceData> {
    return this.http.get<MuscleReferenceData>(this.buildUrl(`/muscles/reference-data`));
  }

  private buildUrl(path: string): string {
    return `${environment.trainingUrl}${path}`;
  }
}
