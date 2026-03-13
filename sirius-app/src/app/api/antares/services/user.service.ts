import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable, tap} from 'rxjs';
import {environment} from '../../../../environments/environment';
import {PreferencesUpdateRequest, ProfileUpdateRequest, UserResponse} from '../models/user.model';
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
