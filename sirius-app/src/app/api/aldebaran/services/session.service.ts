import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpContext, HttpParams} from '@angular/common/http';
import {Observable, Subject} from 'rxjs';
import {environment} from '../../../../environments/environment';
import {Slice} from '../../../core/models/pagination.model';
import {
  GymSessionSettings,
  SessionLogResponse,
  SessionReferenceData,
  SessionRequest,
  SessionResponse,
  SessionScope,
  SessionSummaryResponse,
  SessionType,
  SessionVisibilityRequest
} from '../models/session.model';

/** Filters of the session list; dates are yyyy-MM-dd (a specialty covers its whole week). */
export interface SessionFilters {
  scope?: SessionScope;
  gymId?: number | null;
  from?: string | null;
  to?: string | null;
  type?: SessionType | null;
}

/** Aldebaran sessions (`/sessions`), journal (`/session-logs`) and gym session settings. */
@Injectable({providedIn: 'root'})
export class SessionService {
  private readonly http = inject(HttpClient);
  private readonly refreshNeeded = new Subject<void>();
  /** Emits after a session or a journal changes, so lists refresh. */
  readonly refreshNeeded$ = this.refreshNeeded.asObservable();

  notifyRefresh(): void {
    this.refreshNeeded.next();
  }

  getSessions(filters: SessionFilters = {}, page = 0, size = 50, context?: HttpContext): Observable<Slice<SessionSummaryResponse>> {
    let params = new HttpParams().set('scope', filters.scope ?? 'ALL').set('page', page).set('size', size);
    if (filters.gymId != null) params = params.set('gymId', filters.gymId);
    if (filters.from) params = params.set('from', filters.from);
    if (filters.to) params = params.set('to', filters.to);
    if (filters.type) params = params.set('type', filters.type);
    return this.http.get<Slice<SessionSummaryResponse>>(this.url(), {params, context});
  }

  getSession(id: number): Observable<SessionResponse> {
    return this.http.get<SessionResponse>(this.url(`/${id}`));
  }

  createSession(request: SessionRequest): Observable<SessionResponse> {
    return this.http.post<SessionResponse>(this.url(), request);
  }

  updateSession(id: number, request: SessionRequest): Observable<SessionResponse> {
    return this.http.put<SessionResponse>(this.url(`/${id}`), request);
  }

  updateVisibility(id: number, request: SessionVisibilityRequest): Observable<SessionResponse> {
    return this.http.put<SessionResponse>(this.url(`/${id}/visibility`), request);
  }

  deleteSession(id: number): Observable<void> {
    return this.http.delete<void>(this.url(`/${id}`));
  }

  getReferenceData(): Observable<SessionReferenceData> {
    return this.http.get<SessionReferenceData>(this.url('/reference-data'));
  }

  /** "Séance faite": the current user's journal of the session, every block checked. */
  logSession(id: number, date?: string | null, gymId?: number | null): Observable<SessionLogResponse> {
    return this.http.post<SessionLogResponse>(this.url(`/${id}/logs`), {date: date ?? null, gymId: gymId ?? null});
  }

  getMyLogs(from?: string | null, to?: string | null, page = 0, size = 20, context?: HttpContext): Observable<Slice<SessionLogResponse>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http.get<Slice<SessionLogResponse>>(`${environment.trainingUrl}/session-logs/me`, {params, context});
  }

  setBlockDone(logId: number, blockId: number, done: boolean): Observable<SessionLogResponse> {
    return this.http.put<SessionLogResponse>(`${environment.trainingUrl}/session-logs/${logId}/blocks/${blockId}`, {done});
  }

  deleteLog(logId: number): Observable<void> {
    return this.http.delete<void>(`${environment.trainingUrl}/session-logs/${logId}`);
  }

  getSettings(gymId: number): Observable<GymSessionSettings> {
    return this.http.get<GymSessionSettings>(`${environment.trainingUrl}/gyms/${gymId}/session-settings`);
  }

  updateSettings(gymId: number, settings: GymSessionSettings): Observable<GymSessionSettings> {
    return this.http.put<GymSessionSettings>(`${environment.trainingUrl}/gyms/${gymId}/session-settings`, settings);
  }

  private url(path = ''): string {
    return `${environment.trainingUrl}/sessions${path}`;
  }
}
