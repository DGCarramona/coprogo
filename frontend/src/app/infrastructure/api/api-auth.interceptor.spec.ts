import { HttpHandlerFn, HttpHeaders, HttpRequest, HttpResponse } from '@angular/common/http';
import { firstValueFrom, of } from 'rxjs';

import { StubGoogleIdTokenPort } from '../../../../__test__/app/application/auth/stub-google-id-token.port';
import { GoogleIdTokenPort } from '../../application/auth/google-id-token.port';
import { createApiAuthInterceptor } from './api-auth.interceptor';

describe('createApiAuthInterceptor', () => {
  let tokenStore: GoogleIdTokenPort;
  let interceptedRequest: HttpRequest<unknown>;
  let interceptor: ReturnType<typeof createApiAuthInterceptor>;

  const next: HttpHandlerFn = (request) => {
    interceptedRequest = request;
    return of(new HttpResponse({ body: {} }));
  };

  beforeEach(() => {
    tokenStore = new StubGoogleIdTokenPort();
    interceptor = createApiAuthInterceptor(tokenStore);
  });

  it('adds the stored Google bearer token when no authorization header is present', async () => {
    tokenStore.store('google-id-token');
    const response = firstValueFrom(interceptor(new HttpRequest('GET', '/api/test'), next));

    expect(interceptedRequest.headers.get('Authorization')).toBe('Bearer google-id-token');

    await response;
  });

  it('does not add an authorization header when no token is stored', async () => {
    const response = firstValueFrom(interceptor(new HttpRequest('GET', '/api/test'), next));

    expect(interceptedRequest.headers.has('Authorization')).toBe(false);

    await response;
  });

  it('keeps an existing authorization header untouched', async () => {
    tokenStore.store('google-id-token');
    const request = new HttpRequest('GET', '/api/test', null, {
      headers: new HttpHeaders({ Authorization: 'Bearer existing-token' }),
    });
    const response = firstValueFrom(interceptor(request, next));

    expect(interceptedRequest).toBe(request);
    expect(interceptedRequest.headers.get('Authorization')).toBe('Bearer existing-token');

    await response;
  });
});
