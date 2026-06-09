import { HTTP_INTERCEPTORS } from '@angular/common/http';
import { InjectionToken, makeEnvironmentProviders } from '@angular/core';

import { provideDefaultClient } from './generated';
import { ApiAuthInterceptor } from './api-auth.interceptor';

export interface ApiClientOptions {
  basePath?: string;
}

const DEFAULT_API_BASE_PATH = 'http://localhost:8080';

type ApiClientEnvs = Pick<ImportMetaEnv, 'APP_API_BASE_URL'>;

export const API_BASE_PATH = new InjectionToken<string>('API_BASE_PATH');

export const resolveApiBasePath = (
  options: ApiClientOptions = {},
  env: ApiClientEnvs = import.meta.env,
) => options.basePath ?? env.APP_API_BASE_URL ?? DEFAULT_API_BASE_PATH;

export function provideApiClient(options: ApiClientOptions = {}) {
  const basePath = resolveApiBasePath(options);

  return makeEnvironmentProviders([
    {
      provide: API_BASE_PATH,
      useValue: basePath,
    },
    {
      provide: HTTP_INTERCEPTORS,
      useClass: ApiAuthInterceptor,
      multi: true,
    },
    provideDefaultClient({
      basePath,
    }),
  ]);
}
