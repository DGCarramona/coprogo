import { HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Injectable } from '@angular/core';

import { GoogleIdTokenPort } from '../../application/auth/google-id-token.port';

@Injectable()
export class ApiAuthInterceptor implements HttpInterceptor {
  constructor(private readonly googleIdTokenPort: GoogleIdTokenPort) {}

  intercept(request: HttpRequest<unknown>, next: HttpHandler) {
    if (request.headers.has('Authorization')) {
      return next.handle(request);
    }

    const idToken = this.googleIdTokenPort.currentToken();
    if (idToken === null) {
      return next.handle(request);
    }

    return next.handle(
      request.clone({
        setHeaders: {
          Authorization: `Bearer ${idToken}`,
        },
      }),
    );
  }
}
