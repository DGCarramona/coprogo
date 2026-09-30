import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';

import { GroupsService } from './generated';
import { provideApiClient, resolveApiBasePath } from './provide-api-client';

describe('API client providers', () => {
  describe('resolveApiBasePath', () => {
    it.each([
      {
        name: 'provided base path over the runtime environment',
        options: { basePath: 'https://override.example' },
        environment: { APP_API_BASE_URL: 'https://env.example' },
        expected: 'https://override.example',
      },
      {
        name: 'runtime environment base path without an override',
        options: {},
        environment: { APP_API_BASE_URL: 'https://env.example' },
        expected: 'https://env.example',
      },
      {
        name: 'default base path without an override or environment value',
        options: {},
        environment: {},
        expected: 'http://localhost:8080',
      },
    ])('resolves the $name', ({ options, environment, expected }) => {
      expect(resolveApiBasePath(options, environment)).toBe(expected);
    });
  });

  describe('provideApiClient', () => {
    it('provides the base path to generated services', async () => {
      TestBed.configureTestingModule({
        providers: [
          provideHttpClient(),
          provideHttpClientTesting(),
          provideApiClient({
            basePath: 'http://localhost:8080',
          }),
        ],
      });

      const responsePromise = firstValueFrom(TestBed.inject(GroupsService).listPending());

      const request = TestBed.inject(HttpTestingController).expectOne(
        'http://localhost:8080/api/group-invitations/pending',
      );

      expect(request.request.method).toBe('GET');

      request.flush([]);

      await responsePromise;
      TestBed.inject(HttpTestingController).verify();
    });
  });
});
