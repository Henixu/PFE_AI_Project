import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AuthService } from '../services/auth.service';

const PUBLIC_ENDPOINTS = ['/auth/login', '/auth/forgot-password', '/auth/reset-password'];

/**
 * Ajoute le jeton uniquement aux appels vers notre API (jamais vers un autre domaine)
 * et traite les réponses 401 (session invalide) et 403 (droits insuffisants).
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  const isApi = req.url.startsWith(`${environment.apiUrl}/`);
  const isPublic = PUBLIC_ENDPOINTS.some((p) => req.url === `${environment.apiUrl}${p}`);
  if (!isApi || isPublic) {
    return next(req);
  }

  const token = auth.token;
  const authReq = token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(authReq).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401) {
        auth.logout(true, router.url.startsWith('/login') ? undefined : router.url);
      } else if (err.status === 403) {
        void router.navigate(['/forbidden']);
      }
      return throwError(() => err);
    })
  );
};
