import {inject} from '@angular/core';
import {HttpInterceptorFn} from '@angular/common/http';
import {takeUntil} from 'rxjs';
import {UserSelectionService} from './user-selection.service';

export const userContextInterceptor: HttpInterceptorFn = (request, next) => {
  const selection = inject(UserSelectionService);
  const url = new URL(request.url, location.origin);
  const browserApi = url.origin === location.origin &&
    ((url.pathname.startsWith('/backend/api/') && !url.pathname.startsWith('/backend/api/v1/ingest/')) ||
      url.pathname.startsWith('/api/finances/'));
  if (!browserApi) return next(request);
  const id = selection.target();
  const scoped = id == null ? request : request.clone({setHeaders: {'X-Thereabout-Impersonate-User': String(id)}});
  return next(scoped).pipe(takeUntil(selection.changed));
};
