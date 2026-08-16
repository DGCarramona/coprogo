import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';

import { StubGoogleIdTokenPort } from '../../../../__test__/app/application/auth/stub-google-id-token.port';
import { GoogleIdTokenPort } from '../../application/auth/google-id-token.port';
import { GroupsService } from './generated';
import { provideApiClient, resolveApiBasePath } from './provide-api-client';

describe('provideApiClient', () => {
  it('prefers the provided base path over the runtime environment', () => {
    expect(resolveApiBasePath({ basePath: 'https://override.example' })).toBe(
      'https://override.example',
    );
  });

  it('falls back to the runtime environment base path when no override is provided', () => {
    expect(resolveApiBasePath({}, { APP_API_BASE_URL: 'https://env.example' })).toBe(
      'https://env.example',
    );
  });

  it('falls back to the default base path when no override or environment value is provided', () => {
    expect(resolveApiBasePath({}, {})).toBe('http://localhost:8080');
  });

  it('provides the base path to generated services and adds auth interceptor', async () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        {
          provide: GoogleIdTokenPort,
          useClass: StubGoogleIdTokenPort,
        },
        provideApiClient({
          basePath: 'http://localhost:8080',
        }),
      ],
    });
    TestBed.inject(GoogleIdTokenPort).store('google-id-token');

    const responsePromise = firstValueFrom(TestBed.inject(GroupsService).listPending());

    const request = TestBed.inject(HttpTestingController).expectOne(
      'http://localhost:8080/api/group-invitations/pending',
    );

    expect(request.request.method).toBe('GET');
    expect(request.request.headers.get('Authorization')).toBe('Bearer google-id-token');

    request.flush([]);

    await responsePromise;
    TestBed.inject(HttpTestingController).verify();
  });
});
