import {HttpErrorResponse, HttpHandlerFn, HttpInterceptorFn, HttpRequest} from '@angular/common/http';
import {inject, Injector} from '@angular/core';
import {Router} from '@angular/router';
import {catchError, switchMap, throwError} from 'rxjs';
import {environment} from '../../../environments/environment';
import {AuthService} from '../../api/antares/services/auth.service';
import {SUPPORT_SESSION_EXPIRED} from '../../api/antares/models/support.model';
import {SupportSessionService} from '../services/support-session.service';

const isApiUrl = (url: string): boolean => url.startsWith(environment.authUrl) || url.startsWith(environment.trainingUrl);
const isAuthUrl = (url: string): boolean => url.startsWith(`${environment.authUrl}/auth/`);

export const authInterceptor: HttpInterceptorFn = (req: HttpRequest<unknown>, next: HttpHandlerFn) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  // Resolved lazily: SupportSessionService depends on TranslateService, whose translation loading
  // goes through this interceptor (eager injection is a circular dependency, NG0200).
  const injector = inject(Injector);

  // Clone the request to add 'withCredentials: true' if it targets the API.
  const authorizedReq = isApiUrl(req.url)
    ? req.clone({withCredentials: true})
    : req;

  return next(authorizedReq).pipe(
    catchError((error: HttpErrorResponse) => {
      // An expired support session must never be refreshed into the admin identity: reset instead.
      if (error.status === 401 && error.error?.code === SUPPORT_SESSION_EXPIRED) {
        injector.get(SupportSessionService).handleExpired();
        return throwError(() => error);
      }

      // If the error is not a 401, or if it's on a non-API or auth URL, re-throw it.
      if (error.status !== 401 || !isApiUrl(req.url) || isAuthUrl(req.url)) {
        return throwError(() => error);
      }

      // It's a 401 error on a protected API route, attempt to refresh the token.
      return authService.refreshToken().pipe(
        switchMap(() => {
          // If the refresh is successful, retry the original request.
          // The browser will now have the new cookies set by the refresh response.
          return next(authorizedReq);
        }),
        catchError((refreshErr: HttpErrorResponse) => {
          // No known user (anonymous app load): nothing to log out, the caller (authGuard) redirects.
          if (!authService.isAuthenticated()) {
            return throwError(() => refreshErr);
          }
          // If the refresh fails, log the user out and redirect to the login page.
          authService.logout().subscribe(() => {
            void router.navigate(['/auth/login'], {queryParams: {returnUrl: router.url}});
          });
          return throwError(() => refreshErr);
        })
      );
    })
  );
};
