import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { StubGoogleIdTokenPort } from '../../../../__test__/app/application/auth/stub-google-id-token.port';
import { GoogleIdTokenPort } from '../../application/auth/google-id-token.port';
import { ApiClientError } from '../api/api-client.error';
import { apiAuthInterceptor } from '../api/api-auth.interceptor';
import { provideApiClient } from '../api/provide-api-client';
import { HttpCashPoolHistoryGateway } from './http-cash-pool-history.gateway';

const GROUP_ID = '9d88fb48-4e2c-4a89-8b6b-509ed8f00b93';

describe('HttpCashPoolHistoryGateway', () => {
  let gateway: HttpCashPoolHistoryGateway;
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
    gateway = TestBed.inject(HttpCashPoolHistoryGateway);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTestingController.verify());

  describe('get', () => {
    it('maps income distributions and withdrawals without nullable union fields', async () => {
      const history = gateway.get(GROUP_ID);

      httpTestingController
        .expectOne(`http://localhost:8080/api/groups/${GROUP_ID}/cash-pool/history`)
        .flush({
          group: GROUP_ID,
          operations: [
            {
              type: 'WITHDRAWAL',
              operation: 'withdrawal-1',
              occurredAt: '2026-10-02T10:00:00Z',
              withdrawnBy: 'alice@example.com',
              amountInCents: 7000,
              ownRevenueShareConsumedInCents: 6000,
            },
            {
              type: 'INCOME',
              operation: 'income-1',
              occurredAt: '2026-10-01T10:00:00Z',
              amountInCents: 10000,
              allocations: [
                { member: 'alice@example.com', amountInCents: 6000 },
                { member: 'bob@example.com', amountInCents: 4000 },
              ],
            },
          ],
        });

      await expect(history).resolves.toEqual({
        groupId: GROUP_ID,
        operations: [
          {
            type: 'WITHDRAWAL',
            operationId: 'withdrawal-1',
            occurredAt: new Date('2026-10-02T10:00:00Z'),
            withdrawnBy: 'alice@example.com',
            amountInCents: 7000,
            ownRevenueShareConsumedInCents: 6000,
          },
          {
            type: 'INCOME',
            operationId: 'income-1',
            occurredAt: new Date('2026-10-01T10:00:00Z'),
            amountInCents: 10000,
            allocations: [
              { member: 'alice@example.com', amountInCents: 6000 },
              { member: 'bob@example.com', amountInCents: 4000 },
            ],
          },
        ],
      });
    });

    it('maps API failures', async () => {
      const history = gateway.get(GROUP_ID);

      httpTestingController
        .expectOne(`http://localhost:8080/api/groups/${GROUP_ID}/cash-pool/history`)
        .flush({ message: 'Historique indisponible.' }, { status: 503, statusText: 'Unavailable' });

      await expect(history).rejects.toEqual(new ApiClientError('Historique indisponible.', 503));
    });
  });
});
