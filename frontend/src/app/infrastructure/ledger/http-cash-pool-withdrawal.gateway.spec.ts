import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { StubGoogleIdTokenPort } from '../../../../__test__/app/application/auth/stub-google-id-token.port';
import { GoogleIdTokenPort } from '../../application/auth/google-id-token.port';
import {
  InsufficientCashPoolBalanceError,
  type CashPoolWithdrawalCommand,
} from '../../application/ledger/cash-pool-withdrawal.port';
import { ApiClientError } from '../api/api-client.error';
import { apiAuthInterceptor } from '../api/api-auth.interceptor';
import { provideApiClient } from '../api/provide-api-client';
import { HttpCashPoolWithdrawalGateway } from './http-cash-pool-withdrawal.gateway';

const GROUP_ID = '9d88fb48-4e2c-4a89-8b6b-509ed8f00b93';

describe('HttpCashPoolWithdrawalGateway', () => {
  let gateway: HttpCashPoolWithdrawalGateway;
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
    gateway = TestBed.inject(HttpCashPoolWithdrawalGateway);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTestingController.verify());

  describe('withdraw', () => {
    it('records the amount and withdrawal instant', async () => {
      const withdrawal = gateway.withdraw(command());

      const request = httpTestingController.expectOne(
        `http://localhost:8080/api/groups/${GROUP_ID}/cash-pool-withdrawals`,
      );
      expect({ method: request.request.method, body: request.request.body }).toEqual({
        method: 'POST',
        body: {
          amountInCents: 2500,
          withdrawnAt: '2026-10-03T14:30:00.000Z',
        },
      });
      request.flush(null);

      await expect(withdrawal).resolves.toBeUndefined();
    });

    it('maps an insufficient balance to an application error with a user-facing message', async () => {
      const withdrawal = gateway.withdraw(command());

      httpTestingController
        .expectOne(`http://localhost:8080/api/groups/${GROUP_ID}/cash-pool-withdrawals`)
        .flush(
          { message: 'cash pool withdrawal amount 2500 exceeds available amount 1000' },
          { status: 409, statusText: 'Conflict' },
        );

      await expect(withdrawal).rejects.toEqual(
        new InsufficientCashPoolBalanceError(
          'Le montant dépasse ce qui est disponible dans la caisse commune.',
        ),
      );
    });

    it('maps another API failure to an ApiClientError', async () => {
      const withdrawal = gateway.withdraw(command());

      httpTestingController
        .expectOne(`http://localhost:8080/api/groups/${GROUP_ID}/cash-pool-withdrawals`)
        .flush(
          { message: 'Retrait indisponible.' },
          { status: 503, statusText: 'Service Unavailable' },
        );

      await expect(withdrawal).rejects.toBeInstanceOf(ApiClientError);
      await expect(withdrawal).rejects.toMatchObject({
        message: 'Retrait indisponible.',
        status: 503,
      });
    });
  });
});

const command = (): CashPoolWithdrawalCommand => ({
  groupId: GROUP_ID,
  amountInCents: 2500,
  withdrawnAt: new Date('2026-10-03T14:30:00Z'),
});
