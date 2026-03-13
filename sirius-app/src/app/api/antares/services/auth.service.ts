import {HttpClient} from '@angular/common/http';
import {computed, inject, Injectable, signal} from '@angular/core';
import {Router} from '@angular/router';
import {catchError, Observable, of, tap} from 'rxjs';
import {environment} from '../../../../environments/environment';
import {UserResponse} from '../models/user.model';
import {AuthenticationRequest, RegisterRequest, TokenRefreshResponse} from '../models/auth.model';

@Injectable({providedIn: 'root'})
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly _currentUser = signal<UserResponse | null | undefined>(undefined);
  public readonly currentUser = this._currentUser.asReadonly();
  public readonly isAuthenticated = computed(() => !!this._currentUser());

  initCurrentUser(): Observable<UserResponse | null> {
    return this.http.get<UserResponse>(this.buildUrl('/users/me')).pipe(
      tap(user => this._currentUser.set(user)),
      catchError(() => {
        this._currentUser.set(null);
        return of(null);
      })
    );
  }

  updateCurrentUser(user: UserResponse | null | undefined): void {
    this._currentUser.set(user);
  }

  register(payload: RegisterRequest): Observable<UserResponse> {
    return this.http.post<UserResponse>(this.buildUrl('/auth/register'), payload).pipe(
      tap(user => this._currentUser.set(user))
    );
  }

  login(payload: AuthenticationRequest): Observable<UserResponse> {
    return this.http.post<UserResponse>(this.buildUrl('/auth/login'), payload).pipe(
      tap(user => this._currentUser.set(user))
    );
  }

  logout(): Observable<void> {
    return this.http.post<void>(this.buildUrl('/auth/logout'), null).pipe(
      catchError(() => of(undefined)), // Ensure frontend logout happens even if API fails
      tap(() => {
        this._currentUser.set(null);
        void this.router.navigate(['/auth/login']);
      })
    );
  }

  refreshToken(): Observable<TokenRefreshResponse> {
    return this.http.post<TokenRefreshResponse>(this.buildUrl('/auth/refresh-token'), {});
  }

  private buildUrl(path: string): string {
    return `${environment.authUrl}${path}`;
  }
}
