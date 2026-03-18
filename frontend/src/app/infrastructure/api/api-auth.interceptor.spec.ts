import { HttpHandler, HttpRequest, HttpResponse } from '@angular/common/http';
import { firstValueFrom, of } from 'rxjs';
import { vi } from 'vitest';
import { ApiAuthInterceptor } from './api-auth.interceptor';

describe('ApiAuthInterceptor', () => {
  it('adds a fake bearer token when no authorization header is present', async () => {
    const interceptor = new ApiAuthInterceptor();
    const handle = vi.fn().mockReturnValue(of(new HttpResponse({ status: 200 })));
    const next: HttpHandler = { handle };

    await firstValueFrom(interceptor.intercept(new HttpRequest('GET', '/api/test'), next));

    expect(handle.mock.calls).toEqual([
      [
        {
          body: null,
          cache: undefined,
          context: {
            map: new Map(),
          },
          credentials: undefined,
          headers: {
            headers: new Map(),
            lazyInit: {
              headers: new Map(),
              lazyInit: undefined,
              lazyUpdate: null,
              normalizedNames: new Map(),
            },
            lazyUpdate: [
              {
                name: 'Authorization',
                op: 's',
                value: 'Bearer fake-google-id-token',
              },
            ],
            normalizedNames: new Map(),
          },
          integrity: undefined,
          keepalive: false,
          method: 'GET',
          mode: undefined,
          params: {
            cloneFrom: null,
            encoder: {},
            map: new Map(),
            updates: null,
          },
          priority: undefined,
          redirect: undefined,
          referrer: undefined,
          referrerPolicy: undefined,
          reportProgress: false,
          responseType: 'json',
          timeout: undefined,
          transferCache: undefined,
          url: '/api/test',
          urlWithParams: '/api/test',
          withCredentials: false,
        },
      ],
    ]);
  });

  it('keeps an existing authorization header untouched', async () => {
    const interceptor = new ApiAuthInterceptor();
    const handle = vi.fn().mockReturnValue(of(new HttpResponse({ status: 200 })));
    const next: HttpHandler = { handle };
    const request = new HttpRequest('GET', '/api/test', {
      headers: new Map([['Authorization', 'Bearer existing-token']]),
    });

    await firstValueFrom(interceptor.intercept(request, next));

    expect(handle.mock.calls).toEqual([
      [
        {
          body: null,
          cache: undefined,
          context: {
            map: new Map(),
          },
          credentials: undefined,
          headers: new Map(
            Object.entries({
              Authorization: 'Bearer existing-token',
            }),
          ),
          integrity: undefined,
          keepalive: false,
          method: 'GET',
          mode: undefined,
          params: {
            cloneFrom: null,
            encoder: {},
            map: null,
            updates: null,
          },
          priority: undefined,
          redirect: undefined,
          referrer: undefined,
          referrerPolicy: undefined,
          reportProgress: false,
          responseType: 'json',
          timeout: undefined,
          transferCache: undefined,
          url: '/api/test',
          urlWithParams: '/api/test',
          withCredentials: false,
        },
      ],
    ]);
  });
});
