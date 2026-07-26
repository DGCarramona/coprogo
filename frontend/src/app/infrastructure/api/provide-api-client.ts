import { HTTP_INTERCEPTORS } from '@angular/common/http';
import { makeEnvironmentProviders } from '@angular/core';

import { provideApi } from './generated';
import { ApiAuthInterceptor } from './api-auth.interceptor';

export interface ApiClientOptions {
  basePath?: string;
}

const DEFAULT_API_BASE_PATH = 'http://localhost:8080';

type ApiClientEnvs = Pick<ImportMetaEnv, 'APP_API_BASE_URL'>;

export const resolveApiBasePath = (
  options: ApiClientOptions = {},
  env: ApiClientEnvs = import.meta.env,
) => options.basePath ?? env.APP_API_BASE_URL ?? DEFAULT_API_BASE_PATH;

export function provideApiClient(options: ApiClientOptions = {}) {
  const basePath = resolveApiBasePath(options);

  return makeEnvironmentProviders([
    provideApi(basePath),
    {
      provide: HTTP_INTERCEPTORS,
      useClass: ApiAuthInterceptor,
      multi: true,
    },
  ]);
}
