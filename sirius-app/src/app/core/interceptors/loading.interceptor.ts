import {HttpContextToken, HttpInterceptorFn} from '@angular/common/http';
import {inject} from '@angular/core';
import {finalize} from 'rxjs';
import {LoadingService} from '../services/loading.service';

export const BYPASS_LOADER = new HttpContextToken<boolean>(() => false);

export const loadingInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.context.get(BYPASS_LOADER)) {
    return next(req);
  }

  const loadingService = inject(LoadingService);
  loadingService.show();

  return next(req).pipe(
    finalize(() => loadingService.hide())
  );
};
