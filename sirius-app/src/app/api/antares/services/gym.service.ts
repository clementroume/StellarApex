import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpContext, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../../environments/environment';
import {
  GymPublicResponse,
  GymRequest,
  GymResponse,
  GymSettingsRequest,
  GymSettingsResponse,
  GymStatus,
  GymType,
  GymUpdateRequest,
  JoinGymRequest
} from '../models/gym.model';
import {MembershipResponse, MembershipSummary} from '../models/membership.model';
import {DisplayName} from '../models/user.model';
import {Slice} from '../../../core/models/pagination.model';
import {BYPASS_LOADER} from '../../../core/interceptors/loading.interceptor';

@Injectable({providedIn: 'root'})
export class GymService {
  private readonly http = inject(HttpClient);

  /** Asks for a new box/prog (PENDING_APPROVAL until an admin approves it; one request at a time). */
  createGym(payload: GymRequest): Observable<GymResponse> {
    return this.http.post<GymResponse>(this.buildUrl(), payload);
  }

  /** ACTIVE boxes/progs by name and type (discovery), public view only. */
  searchGyms(search = '', type: GymType | null = null, page = 0, size = 20): Observable<Slice<GymPublicResponse>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (search) params = params.set('search', search);
    if (type) params = params.set('type', type);
    return this.http.get<Slice<GymPublicResponse>>(this.buildUrl(), {params, context: new HttpContext().set(BYPASS_LOADER, true)});
  }

  /** Public view of an ACTIVE gym (e.g. the gym of an invitation link). */
  getGym(id: number): Observable<GymPublicResponse> {
    return this.http.get<GymPublicResponse>(this.buildUrl(`/${id}`));
  }

  updateGym(id: number, payload: GymUpdateRequest): Observable<GymSettingsResponse> {
    return this.http.put<GymSettingsResponse>(this.buildUrl(`/${id}`), payload);
  }

  /** Platform admin: approve, reject or suspend. */
  updateStatus(id: number, status: GymStatus): Observable<GymResponse> {
    const params = new HttpParams().set('status', status);
    return this.http.put<GymResponse>(this.buildUrl(`/${id}/status`), {}, {params});
  }

  /** Joins with the enrollment code (PENDING, or ACTIVE with auto-subscription; a former member is reactivated). */
  joinGym(payload: JoinGymRequest): Observable<MembershipSummary> {
    return this.http.post<MembershipSummary>(this.buildUrl('/join'), payload);
  }

  /** Leaves a gym (membership INACTIVE, history kept; a pending request is withdrawn). */
  leaveGym(gymId: number): Observable<void> {
    return this.http.delete<void>(this.buildUrl(`/${gymId}/membership/me`));
  }

  getSettings(gymId: number): Observable<GymSettingsResponse> {
    return this.http.get<GymSettingsResponse>(this.buildUrl(`/${gymId}/settings`));
  }

  updateSettings(gymId: number, payload: GymSettingsRequest): Observable<GymSettingsResponse> {
    return this.http.put<GymSettingsResponse>(this.buildUrl(`/${gymId}/settings`), payload);
  }

  /** New random enrollment code: former invitation links stop working. */
  regenerateEnrollmentCode(gymId: number): Observable<GymSettingsResponse> {
    return this.http.post<GymSettingsResponse>(this.buildUrl(`/${gymId}/settings/enrollment-code`), {});
  }

  /** Makes an ACTIVE member the Owner/Programmer (gym holder only). */
  transferOwnership(gymId: number, membershipId: number): Observable<MembershipResponse[]> {
    return this.http.post<MembershipResponse[]>(this.buildUrl(`/${gymId}/transfer-ownership`), {membershipId});
  }

  /** Usable members of a gym ("First L."), for the staff logging competition scores (SCORE_VERIFY). */
  getAthletes(gymId: number): Observable<DisplayName[]> {
    return this.http.get<DisplayName[]>(this.buildUrl(`/${gymId}/athletes`));
  }

  deleteGym(id: number): Observable<void> {
    return this.http.delete<void>(this.buildUrl(`/${id}`));
  }

  private buildUrl(path: string = ''): string {
    return `${environment.authUrl}/gyms${path}`;
  }
}
