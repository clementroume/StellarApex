import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../../environments/environment';
import {MuscleReferenceData, MuscleRequest, MuscleResponse} from '../models/muscle.model';

@Injectable({providedIn: 'root'})
export class MuscleService {
  private readonly http = inject(HttpClient);

  getMuscles(): Observable<MuscleResponse[]> {
    return this.http.get<MuscleResponse[]>(this.buildUrl('/muscles'));
  }

  getMuscle(id: number): Observable<MuscleResponse> {
    return this.http.get<MuscleResponse>(this.buildUrl(`/muscles/${id}`));
  }

  createMuscle(request: MuscleRequest): Observable<MuscleResponse> {
    return this.http.post<MuscleResponse>(this.buildUrl('/muscles'), request);
  }

  updateMuscle(id: number, request: MuscleRequest): Observable<MuscleResponse> {
    return this.http.put<MuscleResponse>(this.buildUrl(`/muscles/${id}`), request);
  }

  getReferenceData(): Observable<MuscleReferenceData> {
    return this.http.get<MuscleReferenceData>(this.buildUrl(`/muscles/reference-data`));
  }

  private buildUrl(path: string): string {
    return `${environment.trainingUrl}${path}`;
  }
}
