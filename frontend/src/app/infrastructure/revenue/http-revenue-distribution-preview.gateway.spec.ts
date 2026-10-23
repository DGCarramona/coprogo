import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { StubGoogleIdTokenPort } from '../../../../__test__/app/application/auth/stub-google-id-token.port';
import { GoogleIdTokenPort } from '../../application/auth/google-id-token.port';
import type { RevenueDistributionPreview } from '../../domain/revenue/revenue-distribution-preview';
import { ApiClientError } from '../api/api-client.error';
import { apiAuthInterceptor } from '../api/api-auth.interceptor';
import { provideApiClient } from '../api/provide-api-client';
import { HttpRevenueDistributionPreviewGateway } from './http-revenue-distribution-preview.gateway';

const GROUP_ID = '9d88fb48-4e2c-4a89-8b6b-509ed8f00b93';

describe('HttpRevenueDistributionPreviewGateway', () => {
  let gateway: HttpRevenueDistributionPreviewGateway;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([apiAuthInterceptor])),
        provideHttpClientTesting(),
        { provide: GoogleIdTokenPort, useClass: StubGoogleIdTokenPort },
        provideApiClient({ basePath: 'http://localhost:8080' }),
      ],
    });
    gateway = TestBed.inject(HttpRevenueDistributionPreviewGateway);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTestingController.verify());

  describe('preview', () => {
    it('requests and maps the distribution effective on the selected date', async () => {
      const preview = gateway.preview({
        groupId: GROUP_ID,
        amountInCents: 2550,
        effectiveDate: new Date(2026, 10, 1),
      });

      const request = expectPreviewRequest();
      expect({ method: request.request.method, body: request.request.body }).toEqual({
        method: 'POST',
        body: { amountInCents: 2550, effectiveDate: '2026-11-01' },
      });
      request.flush(response());

      const expected: RevenueDistributionPreview = {
        groupId: GROUP_ID,
        effectiveDate: new Date(2026, 10, 1),
        totalAmountInCents: 2550,
        allocations: [
          { member: 'alice@example.com', amountInCents: 1530 },
          { member: 'bob@example.com', amountInCents: 1020 },
        ],
      };
      await expect(preview).resolves.toEqual(expected);
    });

    it.each([
      {
        name: 'another group',
        body: response({ group: 'another-group' }),
        message: 'Prévisualisation incohérente avec le groupe demandé.',
      },
      {
        name: 'an invalid effective date',
        body: response({ effectiveDate: '2026-02-30' }),
        message: 'Date de prévisualisation invalide: 2026-02-30.',
      },
    ])('rejects a response for $name', async ({ body, message }) => {
      const preview = gateway.preview({
        groupId: GROUP_ID,
        amountInCents: 2550,
        effectiveDate: new Date(2026, 10, 1),
      });

      expectPreviewRequest().flush(body);

      await expect(preview).rejects.toThrow(message);
    });

    it('maps an API failure to an ApiClientError', async () => {
      const preview = gateway.preview({
        groupId: GROUP_ID,
        amountInCents: 2550,
        effectiveDate: new Date(2026, 10, 1),
      });

      expectPreviewRequest().flush(
        { message: 'Aucune quote-part ne s’applique à cette date.' },
        { status: 400, statusText: 'Bad Request' },
      );

      await expect(preview).rejects.toBeInstanceOf(ApiClientError);
      await expect(preview).rejects.toMatchObject({
        message: 'Aucune quote-part ne s’applique à cette date.',
        status: 400,
      });
    });
  });

  const expectPreviewRequest = () =>
    httpTestingController.expectOne(
      `http://localhost:8080/api/groups/${GROUP_ID}/revenue-distribution/preview`,
    );
});

const response = (overrides: Record<string, unknown> = {}) => ({
  group: GROUP_ID,
  effectiveDate: '2026-11-01',
  totalAmountInCents: 2550,
  allocations: [
    { memberId: 'alice@example.com', amountInCents: 1530 },
    { memberId: 'bob@example.com', amountInCents: 1020 },
  ],
  ...overrides,
});
