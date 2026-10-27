import {inject, Injectable, signal} from '@angular/core';
import {HttpClient, HttpContext, HttpParams} from '@angular/common/http';
import {Observable, Subject} from 'rxjs';
import {environment} from '../../../../environments/environment';
import {WodCopyRequest, WodFacets, WodReferenceData, WodRequest, WodResponse, WodScope, WodSummaryResponse,} from '../models/wod.model';
import {Slice} from '../../../core/models/pagination.model';

export interface WodListFilters {
  search?: string;
  // Format (FOR_TIME, AMRAP...) and category (GIRLS, OPEN...)
  type?: string;
  category?: string;
  movementId?: number;
  scope?: WodScope;
  // Only meaningful with the GYM scope
  gymId?: number;
  // Personal WODs of this author
  authorId?: number;
}

export type WodFacetFilters = Pick<WodListFilters, 'search' | 'scope' | 'gymId' | 'authorId'>;

@Injectable({providedIn: 'root'})
export class WodService {
  private readonly http = inject(HttpClient);
  public refreshNeeded$ = new Subject<void>();

  getWods(
    filters: WodListFilters = {},
    page: number = 0,
    size: number = 20,
    context?: HttpContext
  ): Observable<Slice<WodSummaryResponse>> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size);

    if (filters.search) params = params.set('search', filters.search);
    if (filters.type) params = params.set('type', filters.type);
    if (filters.category) params = params.set('category', filters.category);
    if (filters.movementId) params = params.set('movementId', filters.movementId);
    if (filters.scope) params = params.set('scope', filters.scope);
    if (filters.gymId) params = params.set('gymId', filters.gymId);
    if (filters.authorId) params = params.set('authorId', filters.authorId);

    return this.http.get<Slice<WodSummaryResponse>>(this.buildUrl(), {params, context});
  }

  getFacets(filters: WodFacetFilters = {}, context?: HttpContext): Observable<WodFacets> {
    let params = new HttpParams();
    if (filters.search) params = params.set('search', filters.search);
    if (filters.scope) params = params.set('scope', filters.scope);
    if (filters.gymId) params = params.set('gymId', filters.gymId);
    if (filters.authorId) params = params.set('authorId', filters.authorId);

    return this.http.get<WodFacets>(this.buildUrl('/facets'), {params, context});
  }

  getWod(id: number): Observable<WodResponse> {
    return this.http.get<WodResponse>(this.buildUrl(`/${id}`));
  }

  createWod(request: WodRequest): Observable<WodResponse> {
    return this.http.post<WodResponse>(this.buildUrl(), request);
  }

  copyWod(id: number, request: WodCopyRequest): Observable<WodResponse> {
    return this.http.post<WodResponse>(this.buildUrl(`/${id}/copy`), request);
  }

  updateWod(id: number, request: WodRequest): Observable<WodResponse> {
    return this.http.put<WodResponse>(this.buildUrl(`/${id}`), request);
  }

  deleteWod(id: number): Observable<void> {
    return this.http.delete<void>(this.buildUrl(`/${id}`));
  }

  getReferenceData(): Observable<WodReferenceData> {
    return this.http.get<WodReferenceData>(this.buildUrl('/reference-data'));
  }

  private buildUrl(path: string = ''): string {
    return `${environment.trainingUrl}/wods${path}`;
  }

  notifyRefresh() {
    this.refreshNeeded$.next();
  }

  // --- UI STATE ---
  public readonly savedSearchQuery = signal<string>('');
  public readonly savedTypeFilter = signal<string>('');
  public readonly savedCategoryFilter = signal<string>('');
  // Scope tab key ('ALL', 'MINE', 'PUBLIC' or 'GYM:<id>'), empty until the user picks one
  public readonly savedScopeTab = signal<string>('');
  // Admin selectors of the "All" tab (mutually exclusive)
  public readonly savedAdminGymId = signal<number | null>(null);
  public readonly savedAdminAuthorId = signal<number | null>(null);
}
