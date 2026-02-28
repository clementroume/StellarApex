import {HttpInterceptorFn} from '@angular/common/http';
import {inject} from '@angular/core';
import {finalize} from 'rxjs';
import {LoadingService} from '../services/loading.service';

/**
 * A functional interceptor that automatically manages a global loading indicator.
 *
 * It calls `loadingService.show()` before any HTTP request is sent and uses the
 * `finalize` operator to guarantee that `loadingService.hide()` is called
 * when the request completes, either successfully or with an error.
 */
export const loadingInterceptor: HttpInterceptorFn = (req, next) => {
  const loadingService = inject(LoadingService);
  loadingService.show();
  return next(req).pipe(
    finalize(() => loadingService.hide())
  );
};
