import { HttpHandler, HttpRequest, HttpResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom, of } from 'rxjs';
import { vi } from 'vitest';

import { ApiAuthInterceptor } from './api-auth.interceptor';
import { BrowserGoogleIdTokenStore } from '../auth/google/browser-google-id-token.store';

describe('ApiAuthInterceptor', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ApiAuthInterceptor, BrowserGoogleIdTokenStore],
    });
    sessionStorage.clear();
  });

  it('adds the stored Google bearer token when no authorization header is present', async () => {
    const tokenStore = TestBed.inject(BrowserGoogleIdTokenStore);
    tokenStore.store('google-id-token');
    const interceptor = TestBed.inject(ApiAuthInterceptor);
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
                value: 'Bearer google-id-token',
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

  it('does not add an authorization header when no token is stored', async () => {
    const interceptor = TestBed.inject(ApiAuthInterceptor);
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
            lazyInit: undefined,
            lazyUpdate: null,
            normalizedNames: new Map(),
          },
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

  it('keeps an existing authorization header untouched', async () => {
    const tokenStore = TestBed.inject(BrowserGoogleIdTokenStore);
    tokenStore.store('google-id-token');
    const interceptor = TestBed.inject(ApiAuthInterceptor);
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
