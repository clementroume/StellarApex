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
  // Sub-category of the format (ROUNDS, CHIPPER...)
  structure?: string;
  category?: string;
  movementId?: number;
  scope?: WodScope;
  // Only meaningful with the GYM scope
  gymId?: number;
  // Personal WODs of this author
  authorId?: number;
}

export type WodFacetFilters = Pick<WodListFilters, 'search' | 'scope' | 'gymId' | 'authorId' | 'type'>;

@Injectable({providedIn: 'root'})
export class WodService {
  private readonly http = inject(HttpClient);
  public refreshNeeded$ = new Subject<void>();

  getWods(
    filters: WodListFilters = {},
    page: number = 0,
    size: number = 20,
    context?: HttpContext,
    sort?: string
  ): Observable<Slice<WodSummaryResponse>> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size);

    if (filters.search) params = params.set('search', filters.search);
    if (filters.type) params = params.set('type', filters.type);
    if (filters.structure) params = params.set('structure', filters.structure);
    if (filters.category) params = params.set('category', filters.category);
    if (filters.movementId) params = params.set('movementId', filters.movementId);
    if (filters.scope) params = params.set('scope', filters.scope);
    if (filters.gymId) params = params.set('gymId', filters.gymId);
    if (filters.authorId) params = params.set('authorId', filters.authorId);

    // "title,asc" (catalogue A to Z), "title,desc" (latest competitions first)
    if (sort) params = params.set('sort', sort);

    return this.http.get<Slice<WodSummaryResponse>>(this.buildUrl(), {params, context});
  }

  /** Number of WODs for the list filters (numbered pagination of the catalogue). */
  countWods(filters: WodListFilters = {}, context?: HttpContext): Observable<number> {
    let params = new HttpParams();
    if (filters.search) params = params.set('search', filters.search);
    if (filters.type) params = params.set('type', filters.type);
    if (filters.structure) params = params.set('structure', filters.structure);
    if (filters.category) params = params.set('category', filters.category);
    if (filters.scope) params = params.set('scope', filters.scope);
    return this.http.get<number>(this.buildUrl('/count'), {params, context});
  }

  getFacets(filters: WodFacetFilters = {}, context?: HttpContext): Observable<WodFacets> {
    let params = new HttpParams();
    if (filters.search) params = params.set('search', filters.search);
    if (filters.scope) params = params.set('scope', filters.scope);
    if (filters.gymId) params = params.set('gymId', filters.gymId);
    if (filters.authorId) params = params.set('authorId', filters.authorId);
    // Sub-categories present for this format
    if (filters.type) params = params.set('type', filters.type);

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
  public readonly savedStructureFilter = signal<string>('');
  public readonly savedPage = signal<number>(0);
}
