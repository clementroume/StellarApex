import {inject, Injectable, signal} from '@angular/core';
import {HttpClient, HttpContext} from '@angular/common/http';
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

  getMovements(context?: HttpContext): Observable<MovementSummaryResponse[]> {
    return this.http.get<MovementSummaryResponse[]>(this.buildUrl(), {context});
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

  // --- UI STATE ---
  public readonly savedSearchQuery = signal<string>('');
  public readonly savedActiveTab = signal<string>('');
  public readonly savedExpandedCategories = signal<Set<string>>(new Set());

  toggleCategoryExpansion(category: string, isExpanded: boolean): void {
    const currentSet = new Set(this.savedExpandedCategories());
    if (isExpanded) {
      currentSet.add(category);
    } else {
      currentSet.delete(category);
    }
    this.savedExpandedCategories.set(currentSet);
  }
}
