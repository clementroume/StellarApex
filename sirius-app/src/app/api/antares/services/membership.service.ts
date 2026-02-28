import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../../environments/environment';
import {
  MembershipResponse,
  MembershipStatus,
  MembershipUpdateRequest
} from '../models/membership.model';

@Injectable({providedIn: 'root'})
export class MembershipService {
  private readonly http = inject(HttpClient);

  getMemberships(gymId: number, status?: MembershipStatus): Observable<MembershipResponse[]> {
    let params = new HttpParams().set('gymId', gymId);
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<MembershipResponse[]>(this.buildUrl('/memberships'), {params});
  }

  updateMembership(id: number, payload: MembershipUpdateRequest): Observable<MembershipResponse> {
    return this.http.put<MembershipResponse>(this.buildUrl(`/memberships/${id}`), payload);
  }

  deleteMembership(id: number): Observable<void> {
    return this.http.delete<void>(this.buildUrl(`/memberships/${id}`));
  }

  private buildUrl(path: string): string {
    return `${environment.authUrl}${path}`;
  }
}
