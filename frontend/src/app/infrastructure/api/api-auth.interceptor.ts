import { HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Injectable } from '@angular/core';

import { BrowserGoogleIdTokenStore } from '../auth/google/browser-google-id-token.store';

@Injectable()
export class ApiAuthInterceptor implements HttpInterceptor {
  constructor(private readonly googleIdTokenStore: BrowserGoogleIdTokenStore) {}

  intercept(request: HttpRequest<unknown>, next: HttpHandler) {
    if (request.headers.has('Authorization')) {
      return next.handle(request);
    }

    const idToken = this.googleIdTokenStore.currentToken();
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
