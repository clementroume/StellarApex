import {inject, Injectable, signal} from '@angular/core';
import {HttpClient, HttpContext} from '@angular/common/http';
import {Observable, Subject} from 'rxjs';
import {environment} from '../../../../environments/environment';
import {MuscleReferenceData, MuscleRequest, MuscleResponse} from '../models/muscle.model';

@Injectable({providedIn: 'root'})
export class MuscleService {
  private readonly http = inject(HttpClient);
  public refreshNeeded$ = new Subject<void>();

  getMuscles(context?: HttpContext): Observable<MuscleResponse[]> {
    return this.http.get<MuscleResponse[]>(this.buildUrl(), {context});
  }

  getMuscle(id: number): Observable<MuscleResponse> {
    return this.http.get<MuscleResponse>(this.buildUrl(`/${id}`));
  }

  createMuscle(request: MuscleRequest): Observable<MuscleResponse> {
    return this.http.post<MuscleResponse>(this.buildUrl(), request);
  }

  updateMuscle(id: number, request: MuscleRequest): Observable<MuscleResponse> {
    return this.http.put<MuscleResponse>(this.buildUrl(`/${id}`), request);
  }

  deleteMuscle(id: number): Observable<void> {
    return this.http.delete<void>(this.buildUrl(`/${id}`));
  }

  getReferenceData(): Observable<MuscleReferenceData> {
    return this.http.get<MuscleReferenceData>(this.buildUrl(`/reference-data`));
  }

  private buildUrl(path: string = ''): string {
    return `${environment.trainingUrl}/muscles${path}`;
  }

  notifyRefresh() {
    this.refreshNeeded$.next();
  }

  // --- UI STATE ---
  public readonly savedSearchQuery = signal<string>('');
  public readonly savedExpandedGroups = signal<Set<string>>(new Set());

  toggleGroupExpansion(group: string, isExpanded: boolean): void {
    const currentSet = new Set(this.savedExpandedGroups());
    if (isExpanded) {
      currentSet.add(group);
    } else {
      currentSet.delete(group);
    }
    this.savedExpandedGroups.set(currentSet);
  }
}
