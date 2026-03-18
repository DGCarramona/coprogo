import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';

import { FAKE_GOOGLE_ID_TOKEN } from './api-auth.interceptor';
import { RevenueDistributionService } from './generated';
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

  it('wraps provideNgOpenapi with the resolved base path and auth interceptor', async () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        provideApiClient({
          basePath: 'http://localhost:8080',
        }),
      ],
    });

    const responsePromise = firstValueFrom(
      TestBed.inject(RevenueDistributionService).preview({
        amountInCents: 100,
        members: [
          {
            memberId: 'alice',
            percentage: 100,
          },
        ],
      }),
    );

    const request = TestBed.inject(HttpTestingController).expectOne(
      'http://localhost:8080/api/revenue-distribution/preview',
    );

    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('Authorization')).toBe(`Bearer ${FAKE_GOOGLE_ID_TOKEN}`);

    request.flush({
      allocations: [
        {
          amountInCents: 100,
          memberId: 'alice',
        },
      ],
      totalAmountInCents: 100,
    });

    const response = await responsePromise;

    expect(response.totalAmountInCents).toBe(100);
    TestBed.inject(HttpTestingController).verify();
  });
});
