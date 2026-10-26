import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../../environments/environment';
import {UserResponse, UserSummary} from '../models/user.model';
import {GymRole, MembershipStatus} from '../models/membership.model';
import {Slice} from '../../../core/models/pagination.model';
import {SupportSessionRequest, SupportSessionResponse} from '../models/support.model';

export interface UserSearchFilters {
  search?: string;
  gymId?: number;
  role?: GymRole;
  status?: MembershipStatus;
}

@Injectable({providedIn: 'root'})
export class AdminService {
  private readonly http = inject(HttpClient);

  searchUsers(filters: UserSearchFilters = {}, page: number = 0, size: number = 20): Observable<Slice<UserResponse>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filters.search) params = params.set('search', filters.search);
    if (filters.gymId) params = params.set('gymId', filters.gymId);
    if (filters.role) params = params.set('role', filters.role);
    if (filters.status) params = params.set('status', filters.status);
    return this.http.get<Slice<UserResponse>>(this.buildUrl('/users'), {params});
  }

  lookupUsers(ids: number[]): Observable<UserSummary[]> {
    const params = new HttpParams().set('ids', ids.join(','));
    return this.http.get<UserSummary[]>(this.buildUrl('/users/lookup'), {params});
  }

  getRecentSupportTargets(): Observable<UserResponse[]> {
    return this.http.get<UserResponse[]>(this.buildUrl('/support-sessions/recent'));
  }

  startSupportSession(request: SupportSessionRequest): Observable<SupportSessionResponse> {
    return this.http.post<SupportSessionResponse>(this.buildUrl('/support-sessions'), request);
  }

  endSupportSession(): Observable<void> {
    return this.http.delete<void>(this.buildUrl('/support-sessions/current'));
  }

  private buildUrl(path: string): string {
    return `${environment.authUrl}/admin${path}`;
  }
}
