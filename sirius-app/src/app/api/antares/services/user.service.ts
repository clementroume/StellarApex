import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpContext} from '@angular/common/http';
import {Observable, tap} from 'rxjs';
import {environment} from '../../../../environments/environment';
import {
  AthleteProfileRequest,
  BodyWeightRequest,
  BodyWeightResponse,
  DisplayName,
  PreferencesUpdateRequest,
  ProfileUpdateRequest,
  UserResponse
} from '../models/user.model';
import {BYPASS_LOADER} from '../../../core/interceptors/loading.interceptor';
import {ChangePasswordRequest} from '../models/auth.model';
import {AuthService} from './auth.service';
import {Router} from '@angular/router';

@Injectable({providedIn: 'root'})
export class UserService {
  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  getProfile(): Observable<UserResponse> {
    return this.http.get<UserResponse>(this.buildUrl('/me'));
  }

  /** Reloads the current user (e.g. after joining or leaving a gym, whose memberships drive the UI rights). */
  refreshCurrentUser(): Observable<UserResponse> {
    return this.getProfile().pipe(tap(user => this.authService.updateCurrentUser(user)));
  }

  updateProfile(payload: ProfileUpdateRequest): Observable<UserResponse> {
    return this.http.put<UserResponse>(this.buildUrl('/me/profile'), payload).pipe(
      tap(user => this.authService.updateCurrentUser(user))
    );
  }

  updatePreferences(payload: PreferencesUpdateRequest): Observable<UserResponse> {
    return this.http.patch<UserResponse>(this.buildUrl('/me/preferences'), payload).pipe(
      tap(user => this.authService.updateCurrentUser(user))
    );
  }

  /** Division, birth year and height; null clears a field. */
  updateAthleteProfile(payload: AthleteProfileRequest): Observable<UserResponse> {
    return this.http.put<UserResponse>(this.buildUrl('/me/athlete-profile'), payload).pipe(
      tap(user => this.authService.updateCurrentUser(user))
    );
  }

  /** The user's body weights (kg), most recent first. */
  getBodyWeights(): Observable<BodyWeightResponse[]> {
    return this.http.get<BodyWeightResponse[]>(this.buildUrl('/me/body-weights'));
  }

  /** Records a body weight (kg); a second measure the same day replaces the first. */
  recordBodyWeight(payload: BodyWeightRequest): Observable<BodyWeightResponse> {
    return this.http.post<BodyWeightResponse>(this.buildUrl('/me/body-weights'), payload);
  }

  deleteBodyWeight(id: number): Observable<void> {
    return this.http.delete<void>(this.buildUrl(`/me/body-weights/${id}`));
  }

  /** Mails a new verification link to the current address (throttled by Antares). */
  resendVerification(): Observable<void> {
    return this.http.post<void>(this.buildUrl('/me/email/verification'), null);
  }

  /** Names the current user may see among these users; hidden users are left out (at most 100). */
  getDisplayNames(userIds: number[]): Observable<DisplayName[]> {
    return this.http.post<DisplayName[]>(this.buildUrl('/display-names'), {userIds},
      {context: new HttpContext().set(BYPASS_LOADER, true)});
  }

  changePassword(payload: ChangePasswordRequest): Observable<void> {
    return this.http.put<void>(this.buildUrl('/me/password'), payload);
  }

  deleteAccount(): Observable<void> {
    return this.http.delete<void>(this.buildUrl('/me')).pipe(
      tap(() => {
        this.authService.updateCurrentUser(null);
        void this.router.navigate(['/auth/register']);
      })
    );
  }

  private buildUrl(path: string): string {
    return `${environment.authUrl}/users${path}`;
  }
}
