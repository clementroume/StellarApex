import {HttpClient} from '@angular/common/http';
import {computed, inject, Injectable, signal} from '@angular/core';
import {Router} from '@angular/router';
import {catchError, Observable, of, tap} from 'rxjs';
import {environment} from '../../../../environments/environment';
import {UserResponse} from '../models/user.model';
import {
  AuthenticationRequest,
  RegisterRequest,
  TokenRefreshResponse
} from '../models/auth.model';

/**
 * A singleton service responsible for all authentication-related operations
 * and for managing the current user's session state using signals.
 */
@Injectable({providedIn: 'root'})
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  // --- State Management with Signals ---

  // Private writable signal for the user state. `undefined` means the state is unknown (initial load).
  private readonly _currentUser = signal<UserResponse | null | undefined>(undefined);

  /** A public readonly signal representing the current authenticated user. */
  public readonly currentUser = this._currentUser.asReadonly();

  /** A public computed signal that is `true` if the user is authenticated. */
  public readonly isAuthenticated = computed(() => !!this._currentUser());

  // --- Initialization ---

  /**
   * Checks the user's authentication status by making a request to the backend.
   * This should be called once when the application initializes (e.g., by the AuthGuard).
   * @returns An Observable that emits the User object or null.
   */
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
   * Allows other services (like UserService) to update the current user state
   * when data changes (e.g. profile update, theme change).
   */
  updateCurrentUser(user: UserResponse | null | undefined): void {
    this._currentUser.set(user);
  }

  // --- Authentication Methods ---

  /**
   * Sends a registration request. On success, sets the current user signal.
   * @param payload The user's registration data.
   */
  register(payload: RegisterRequest): Observable<UserResponse> {
    return this.http.post<UserResponse>(this.buildUrl('/auth/register'), payload).pipe(
      tap(user => this._currentUser.set(user))
    );
  }

  /**
   * Sends a login request. On success, sets the current user signal.
   * @param payload The user's credentials.
   */
  login(payload: AuthenticationRequest): Observable<UserResponse> {
    return this.http.post<UserResponse>(this.buildUrl('/auth/login'), payload).pipe(
      tap(user => this._currentUser.set(user))
    );
  }

  /**
   * Logs the user out, clears the local session state, and redirects to the login page.
   */
  logout(): Observable<void> {
    return this.http.post<void>(this.buildUrl('/auth/logout'), null).pipe(
      catchError(() => of(undefined)), // Ensure frontend logout happens even if API fails
      tap(() => {
        this._currentUser.set(null);
        void this.router.navigate(['/auth/login']);
      })
    );
  }

  /**
   * Requests a new access token from the backend.
   * Called by the `authInterceptor` when a 401 error is detected.
   */
  refreshToken(): Observable<TokenRefreshResponse> {
    return this.http.post<TokenRefreshResponse>(this.buildUrl('/auth/refresh-token'), {});
  }

  // --- Private Helper ---

  /**
   * Constructs a full API URL from a given path.
   * @param path The endpoint path (e.g., '/users/me').
   */
  private buildUrl(path: string): string {
    return `${environment.authUrl}${path}`;
  }
}
