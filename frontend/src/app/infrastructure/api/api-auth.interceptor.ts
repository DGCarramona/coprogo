import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';

import { GoogleIdTokenPort } from '../../application/auth/google-id-token.port';

export const createApiAuthInterceptor =
  (tokenPort: GoogleIdTokenPort): HttpInterceptorFn =>
  (request, next) => {
    if (request.headers.has('Authorization')) {
      return next(request);
    }

    const idToken = tokenPort.currentToken();
    if (idToken === null) return next(request);

    return next(
      request.clone({
        setHeaders: {
          Authorization: `Bearer ${idToken}`,
        },
      }),
    );
  };

export const apiAuthInterceptor: HttpInterceptorFn = (request, next) =>
  createApiAuthInterceptor(inject(GoogleIdTokenPort))(request, next);
