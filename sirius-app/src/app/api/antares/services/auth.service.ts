import {HttpClient} from '@angular/common/http';
import {computed, inject, Injectable, signal} from '@angular/core';
import {Router} from '@angular/router';
import {catchError, Observable, of, tap} from 'rxjs';
import {environment} from '../../../../environments/environment';
import {UserResponse} from '../models/user.model';
import {
  AuthenticationRequest,
  ForgotPasswordRequest,
  RegisterRequest,
  ResetPasswordRequest,
  TokenRefreshResponse,
  VerifyEmailRequest
} from '../models/auth.model';

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

  /**
   * Replaces the current user with a server response (e.g. after a profile update). A response
   * that does not carry `supportSession` keeps the current one: losing it would make the UI think
   * an admin support session ended while the server still runs it.
   */
  updateCurrentUser(user: UserResponse | null | undefined): void {
    const current = this._currentUser();
    if (user && user.supportSession === undefined && current?.id === user.id && current.supportSession) {
      this._currentUser.set({...user, supportSession: current.supportSession});
      return;
    }
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

  /** Asks for a reset link; the answer is the same whether or not the account exists. */
  forgotPassword(payload: ForgotPasswordRequest): Observable<void> {
    return this.http.post<void>(this.buildUrl('/auth/password/forgot'), payload);
  }

  /** Sets a new password with a reset token; every session of the account ends. */
  resetPassword(payload: ResetPasswordRequest): Observable<void> {
    return this.http.post<void>(this.buildUrl('/auth/password/reset'), payload);
  }

  /** Confirms the address with the token of a verification link (works signed out). */
  verifyEmail(payload: VerifyEmailRequest): Observable<void> {
    return this.http.post<void>(this.buildUrl('/auth/email/verify'), payload);
  }

  refreshToken(): Observable<TokenRefreshResponse> {
    return this.http.post<TokenRefreshResponse>(this.buildUrl('/auth/refresh-token'), {});
  }

  private buildUrl(path: string): string {
    return `${environment.authUrl}${path}`;
  }
}
