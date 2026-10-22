import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { StubGoogleIdTokenPort } from '../../../../__test__/app/application/auth/stub-google-id-token.port';
import { GoogleIdTokenPort } from '../../application/auth/google-id-token.port';
import type { OwnershipShareChangeCommand } from '../../application/revenue/ownership-share-change.port';
import { ApiClientError } from '../api/api-client.error';
import { apiAuthInterceptor } from '../api/api-auth.interceptor';
import { provideApiClient } from '../api/provide-api-client';
import { HttpOwnershipShareChangeGateway } from './http-ownership-share-change.gateway';

const GROUP_ID = '9d88fb48-4e2c-4a89-8b6b-509ed8f00b93';

describe('HttpOwnershipShareChangeGateway', () => {
  let gateway: HttpOwnershipShareChangeGateway;
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
    gateway = TestBed.inject(HttpOwnershipShareChangeGateway);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTestingController.verify());

  describe('record', () => {
    it('records the effective date and exact member shares', async () => {
      const recording = gateway.record(command());

      const request = httpTestingController.expectOne(
        `http://localhost:8080/api/groups/${GROUP_ID}/ownership-shares`,
      );
      expect({ method: request.request.method, body: request.request.body }).toEqual({
        method: 'POST',
        body: {
          effectiveDate: '2026-11-01',
          shares: [
            { member: 'alice@example.com', percentage: 60.25 },
            { member: 'bob@example.com', percentage: 39.75 },
          ],
        },
      });
      request.flush(null);

      await expect(recording).resolves.toBeUndefined();
    });

    it('maps an API failure to an ApiClientError', async () => {
      const recording = gateway.record(command());

      httpTestingController
        .expectOne(`http://localhost:8080/api/groups/${GROUP_ID}/ownership-shares`)
        .flush(
          { message: 'Seul le créateur du groupe peut modifier les quotes-parts.' },
          { status: 403, statusText: 'Forbidden' },
        );

      await expect(recording).rejects.toBeInstanceOf(ApiClientError);
      await expect(recording).rejects.toMatchObject({
        message: 'Seul le créateur du groupe peut modifier les quotes-parts.',
        status: 403,
      });
    });
  });
});

const command = (): OwnershipShareChangeCommand => ({
  groupId: GROUP_ID,
  effectiveDate: new Date(2026, 10, 1),
  shares: [
    { member: 'alice@example.com', percentage: 60.25 },
    { member: 'bob@example.com', percentage: 39.75 },
  ],
});
