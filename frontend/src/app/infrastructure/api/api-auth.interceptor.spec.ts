import { HttpHandler, HttpHeaders, HttpRequest, HttpResponse } from '@angular/common/http';
import { firstValueFrom, of } from 'rxjs';
import { vi } from 'vitest';

import { ApiAuthInterceptor } from './api-auth.interceptor';
import { BrowserGoogleIdTokenStore } from '../auth/google/browser-google-id-token.store';

describe('ApiAuthInterceptor', () => {
  const tokenStore = new BrowserGoogleIdTokenStore();
  const interceptor = new ApiAuthInterceptor(tokenStore);

  beforeEach(() => {
    sessionStorage.clear();
  });

  it('adds the stored Google bearer token when no authorization header is present', async () => {
    tokenStore.store('google-id-token');
    const { handle, next } = nextHandler();

    await firstValueFrom(interceptor.intercept(new HttpRequest('GET', '/api/test'), next));
    expect(sentRequest(handle).headers.get('Authorization')).toBe('Bearer google-id-token');
  });

  it('does not add an authorization header when no token is stored', async () => {
    const { handle, next } = nextHandler();

    await firstValueFrom(interceptor.intercept(new HttpRequest('GET', '/api/test'), next));
    expect(sentRequest(handle).headers.has('Authorization')).toBe(false);
  });

  it('keeps an existing authorization header untouched', async () => {
    tokenStore.store('google-id-token');
    const { handle, next } = nextHandler();
    const request = new HttpRequest('GET', '/api/test', {
      headers: new HttpHeaders({ Authorization: 'Bearer existing-token' }),
    });

    await firstValueFrom(interceptor.intercept(request, next));
    expect(sentRequest(handle)).toBe(request);
    expect(sentRequest(handle).headers.get('Authorization')).toBe('Bearer existing-token');
  });
});

const nextHandler = (): { handle: ReturnType<typeof vi.fn>; next: HttpHandler } => {
  const handle = vi.fn().mockReturnValue(of(new HttpResponse({ status: 200 })));
  return { handle, next: { handle } };
};

const sentRequest = (handle: ReturnType<typeof vi.fn>): HttpRequest<unknown> =>
  handle.mock.calls[0][0] as HttpRequest<unknown>;
