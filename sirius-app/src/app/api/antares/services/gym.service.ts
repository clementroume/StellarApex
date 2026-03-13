import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../../environments/environment';
import {GymRequest, GymResponse, GymSettingsRequest, GymStatus, JoinGymRequest} from '../models/gym.model';
import {MembershipResponse} from '../models/membership.model';

@Injectable({providedIn: 'root'})
export class GymService {
  private readonly http = inject(HttpClient);

  createGym(payload: GymRequest): Observable<GymResponse> {
    return this.http.post<GymResponse>(this.buildUrl(), payload);
  }

  getGyms(status?: GymStatus): Observable<GymResponse[]> {
    let params = new HttpParams();
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<GymResponse[]>(this.buildUrl(), {params});
  }

  updateStatus(id: number, status: GymStatus): Observable<GymResponse> {
    const params = new HttpParams().set('status', status);
    return this.http.put<GymResponse>(this.buildUrl(`/${id}/status`), {}, {params});
  }

  joinGym(payload: JoinGymRequest): Observable<MembershipResponse> {
    return this.http.post<MembershipResponse>(this.buildUrl('/join'), payload);
  }

  getSettings(gymId: number): Observable<GymSettingsRequest> {
    return this.http.get<GymSettingsRequest>(this.buildUrl(`/${gymId}/settings`));
  }

  updateSettings(gymId: number, payload: GymSettingsRequest): Observable<void> {
    return this.http.put<void>(this.buildUrl(`/${gymId}/settings`), payload);
  }

  deleteGym(id: number): Observable<void> {
    return this.http.delete<void>(this.buildUrl(`/${id}`));
  }

  private buildUrl(path: string = ''): string {
    return `${environment.authUrl}/gyms${path}`;
  }
}
