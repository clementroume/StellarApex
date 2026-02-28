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

  /**
   * Retrieves the profile of the currently authenticated user.
   */
  getProfile(): Observable<UserResponse> {
    return this.http.get<UserResponse>(this.buildUrl('/users/me'));
  }

  /**
   * Updates the user's profile (Name, Email).
   * On success, updates the global auth state.
   */
  updateProfile(payload: ProfileUpdateRequest): Observable<UserResponse> {
    return this.http.put<UserResponse>(this.buildUrl('/users/me/profile'), payload).pipe(
      tap(user => this.authService.updateCurrentUser(user))
    );
  }

  /**
   * Updates the user's preferences (Locale, Theme).
   * On success, updates the global auth state.
   */
  updatePreferences(payload: PreferencesUpdateRequest): Observable<UserResponse> {
    return this.http.patch<UserResponse>(this.buildUrl('/users/me/preferences'), payload).pipe(
      tap(user => this.authService.updateCurrentUser(user))
    );
  }

  /**
   * Changes the current user's password.
   */
  changePassword(payload: ChangePasswordRequest): Observable<void> {
    return this.http.put<void>(this.buildUrl('/users/me/password'), payload);
  }

  /**
   * Deletes the account of the currently authenticated user.
   * On success, clears the auth state and redirects to register.
   */
  deleteAccount(): Observable<void> {
    return this.http.delete<void>(this.buildUrl('/users/me')).pipe(
      tap(() => {
        this.authService.updateCurrentUser(null);
        void this.router.navigate(['/auth/register']);
      })
    );
  }

  private buildUrl(path: string): string {
    return `${environment.authUrl}${path}`;
  }
}
