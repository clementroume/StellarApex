import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../../environments/environment';
import {MuscleGroup, MuscleRequest, MuscleResponse} from '../models/muscle.model';

@Injectable({providedIn: 'root'})
export class MuscleService {
  private readonly http = inject(HttpClient);

  getMuscles(group?: MuscleGroup): Observable<MuscleResponse[]> {
    let params = new HttpParams();
    if (group) {
      params = params.set('group', group);
    }
    return this.http.get<MuscleResponse[]>(this.buildUrl('/muscles'), {params});
  }

  getMuscle(medicalName: string): Observable<MuscleResponse> {
    return this.http.get<MuscleResponse>(this.buildUrl(`/muscles/${medicalName}`));
  }

  createMuscle(request: MuscleRequest): Observable<MuscleResponse> {
    return this.http.post<MuscleResponse>(this.buildUrl('/muscles'), request);
  }

  updateMuscle(id: number, request: MuscleRequest): Observable<MuscleResponse> {
    // Note: Update uses the technical ID (Long), while Get uses the medical name (String)
    return this.http.put<MuscleResponse>(this.buildUrl(`/muscles/${id}`), request);
  }

  private buildUrl(path: string): string {
    return `${environment.trainingUrl}${path}`;
  }
}
