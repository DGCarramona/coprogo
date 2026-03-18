import { HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';

export const FAKE_GOOGLE_ID_TOKEN = 'fake-google-id-token';

export class ApiAuthInterceptor implements HttpInterceptor {
  intercept(request: HttpRequest<unknown>, next: HttpHandler) {
    if (request.headers.has('Authorization')) {
      return next.handle(request);
    }

    return next.handle(
      request.clone({
        setHeaders: {
          Authorization: `Bearer ${FAKE_GOOGLE_ID_TOKEN}`,
        },
      }),
    );
  }
}
