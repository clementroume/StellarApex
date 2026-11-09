import {inject, Injectable, signal} from '@angular/core';
import {HttpClient, HttpContext, HttpParams} from '@angular/common/http';
import {Observable, shareReplay, Subject, tap} from 'rxjs';
import {environment} from '../../../../environments/environment';
import {
  MovementVariant,
  UsualNameOption,
  MovementReferenceData,
  MovementRequest,
  MovementResponse,
  MovementSummaryResponse
} from '../models/movement.model';

@Injectable({providedIn: 'root'})
export class MovementService {
  private readonly http = inject(HttpClient);
  public refreshNeeded$ = new Subject<void>();
  private referenceData$?: Observable<MovementReferenceData>;
  private usualNames$?: Observable<UsualNameOption[]>;

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

  /** Enum groups of the forms: they only change with a release, so one load is enough. */
  getReferenceData(): Observable<MovementReferenceData> {
    this.referenceData$ ??= this.http.get<MovementReferenceData>(this.buildUrl(`/reference-data`))
      .pipe(tap({error: () => this.referenceData$ = undefined}), shareReplay({bufferSize: 1, refCount: false}));
    return this.referenceData$;
  }

  /** Every usual name of the catalog, for the movement search; reloaded after an admin change. */
  getUsualNames(context?: HttpContext): Observable<UsualNameOption[]> {
    this.usualNames$ ??= this.http.get<UsualNameOption[]>(this.buildUrl('/usual-names'), {context})
      .pipe(tap({error: () => this.usualNames$ = undefined}), shareReplay({bufferSize: 1, refCount: false}));
    return this.usualNames$;
  }

  /** A movement for a chosen variant: usual name, roles, description, video. */
  getVariant(id: number, techniques: string[], equipment: string[], context?: HttpContext): Observable<MovementVariant> {
    let params = new HttpParams();
    techniques.forEach(t => params = params.append('techniques', t));
    equipment.forEach(e => params = params.append('equipment', e));
    return this.http.get<MovementVariant>(this.buildUrl(`/${id}/variant`), {params, context});
  }

  private buildUrl(path: string = ''): string {
    return `${environment.trainingUrl}/movements${path}`;
  }

  notifyRefresh() {
    this.usualNames$ = undefined;
    this.refreshNeeded$.next();
  }

  // --- UI STATE ---
  public readonly savedSearchQuery = signal<string>('');
  public readonly savedActiveTab = signal<string>('');
  /** Family picked on the catalog, null for all of the discipline. */
  public readonly savedFamily = signal<string | null>(null);
}
