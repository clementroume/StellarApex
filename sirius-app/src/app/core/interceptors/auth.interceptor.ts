import {HttpErrorResponse, HttpHandlerFn, HttpInterceptorFn, HttpRequest} from '@angular/common/http';
import {inject} from '@angular/core';
import {Router} from '@angular/router';
import {catchError, switchMap, throwError} from 'rxjs';
import {environment} from '../../../environments/environment';
import {AuthService} from '../../api/antares/services/auth.service';

const isApiUrl = (url: string): boolean => url.startsWith(environment.authUrl) || url.startsWith(environment.trainingUrl);
const isAuthUrl = (url: string): boolean => url.startsWith(`${environment.authUrl}/auth/`);

export const authInterceptor: HttpInterceptorFn = (req: HttpRequest<unknown>, next: HttpHandlerFn) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  // Clone the request to add 'withCredentials: true' if it targets the API.
  const authorizedReq = isApiUrl(req.url)
    ? req.clone({withCredentials: true})
    : req;

  return next(authorizedReq).pipe(
    catchError((error: HttpErrorResponse) => {
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
