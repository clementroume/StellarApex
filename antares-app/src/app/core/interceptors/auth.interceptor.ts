import { HttpErrorResponse, HttpHandlerFn, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from '../services/auth.service';
import { TokenRefreshResponse } from '../models/user.model';

/**
 * Checks if a URL targets the backend API.
 * @param url The request URL.
 * @returns `true` if the URL starts with the configured API URL.
 */
const isApiUrl = (url: string): boolean => url.startsWith(`${environment.apiUrl}/api/v1`);

/**
 * Checks if a URL targets an authentication endpoint.
 * These endpoints should be ignored by the refresh-token logic to prevent infinite loops.
 * @param url The request URL.
 * @returns `true` if the URL is an authentication endpoint.
 */
const isAuthUrl = (url: string): boolean => /\/api\/v1\/auth\//.test(url);

/**
 * A functional interceptor that handles two core authentication responsibilities:
 * 1. Adds `withCredentials: true` to all outgoing requests to the API, ensuring cookies are sent.
 * 2. Catches `401 Unauthorized` errors, attempts to refresh the session using a refresh token,
 * and retries the original request upon success. If the refresh fails, it logs the user out.
 */
export const authInterceptor: HttpInterceptorFn = (req: HttpRequest<unknown>, next: HttpHandlerFn) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  // Clone the request to add 'withCredentials: true' if it targets the API.
  const authorizedReq = isApiUrl(req.url)
    ? req.clone({ withCredentials: true })
    : req;

  return next(authorizedReq).pipe(
    catchError((error: HttpErrorResponse) => {
      // If the error is not a 401, or if it's on a non-API or auth URL, re-throw it.
      if (error.status !== 401 || !isApiUrl(req.url) || isAuthUrl(req.url)) {
        return throwError(() => error);
      }

      // It's a 401 error on a protected API route, attempt to refresh the token.
      return authService.refreshToken().pipe(
        switchMap((token: TokenRefreshResponse) => {
          // If the refresh is successful, retry the original request.
          // The browser will now have the new cookies set by the refresh response.
          return next(authorizedReq);
        }),
        catchError((refreshErr: HttpErrorResponse) => {
          // If the refresh fails, log the user out and redirect to the login page.
          authService.logout().subscribe(() => {
            void router.navigate(['/auth/login'], { queryParams: { returnUrl: router.url } });
          });
          return throwError(() => refreshErr);
        })
      );
    })
  );
};
