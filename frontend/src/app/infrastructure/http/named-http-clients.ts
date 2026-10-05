import { HttpBackend, HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { EnvironmentProviders, InjectionToken, makeEnvironmentProviders } from '@angular/core';

import { apiAuthInterceptor } from '../api/api-auth.interceptor';

export const AUTHENTICATED_API_HTTP_CLIENT = new InjectionToken<HttpClient>(
  'AUTHENTICATED_API_HTTP_CLIENT',
);

export const DIRECT_HTTP_CLIENT = new InjectionToken<HttpClient>('DIRECT_HTTP_CLIENT');

const directHttpClientFactory = (httpBackend: HttpBackend): HttpClient =>
  new HttpClient(httpBackend);

export const provideCoprogoHttpClients = (): EnvironmentProviders =>
  makeEnvironmentProviders([
    provideHttpClient(withInterceptors([apiAuthInterceptor])),
    {
      provide: AUTHENTICATED_API_HTTP_CLIENT,
      useExisting: HttpClient,
    },
    {
      provide: DIRECT_HTTP_CLIENT,
      useFactory: directHttpClientFactory,
      deps: [HttpBackend],
    },
  ]);
