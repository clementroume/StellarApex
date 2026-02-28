import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable, tap} from 'rxjs';
import {environment} from '../../../../environments/environment';
import {UserResponse} from '../models/user.model';
import {AuthService} from './auth.service';

@Injectable({providedIn: 'root'})
export class ImpersonationService {
  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);

  /**
   * Allows an admin to impersonate another user.
   * On success, the auth state is updated with the target user's identity.
   */
  impersonate(userId: number): Observable<UserResponse> {
    return this.http.post<UserResponse>(this.buildUrl(`/auth/impersonate/${userId}`), {}).pipe(
      tap(user => this.authService.updateCurrentUser(user))
    );
  }

  private buildUrl(path: string): string {
    return `${environment.authUrl}${path}`;
  }
}
