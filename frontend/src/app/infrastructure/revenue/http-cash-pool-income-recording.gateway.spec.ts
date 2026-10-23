import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { StubGoogleIdTokenPort } from '../../../../__test__/app/application/auth/stub-google-id-token.port';
import { GoogleIdTokenPort } from '../../application/auth/google-id-token.port';
import type { CashPoolIncomeRecordingCommand } from '../../application/revenue/cash-pool-income-recording.port';
import { ApiClientError } from '../api/api-client.error';
import { apiAuthInterceptor } from '../api/api-auth.interceptor';
import { provideApiClient } from '../api/provide-api-client';
import { HttpCashPoolIncomeRecordingGateway } from './http-cash-pool-income-recording.gateway';

const GROUP_ID = '9d88fb48-4e2c-4a89-8b6b-509ed8f00b93';

describe('HttpCashPoolIncomeRecordingGateway', () => {
  let gateway: HttpCashPoolIncomeRecordingGateway;
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
    gateway = TestBed.inject(HttpCashPoolIncomeRecordingGateway);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTestingController.verify());

  describe('record', () => {
    it('records the received income and its ownership-share effective date', async () => {
      const recording = gateway.record(command());

      const request = httpTestingController.expectOne(
        `http://localhost:8080/api/groups/${GROUP_ID}/cash-pool-incomes`,
      );
      expect({ method: request.request.method, body: request.request.body }).toEqual({
        method: 'POST',
        body: {
          amountInCents: 125050,
          receivedAt: '2026-10-01T09:30:00.000Z',
          effectiveDate: '2026-09-01',
        },
      });
      request.flush(null);

      await expect(recording).resolves.toBeUndefined();
    });

    it('maps an API failure to an ApiClientError', async () => {
      const recording = gateway.record(command());

      httpTestingController
        .expectOne(`http://localhost:8080/api/groups/${GROUP_ID}/cash-pool-incomes`)
        .flush(
          { message: 'Aucune quote-part ne s’applique à cette date.' },
          { status: 400, statusText: 'Bad Request' },
        );

      await expect(recording).rejects.toBeInstanceOf(ApiClientError);
      await expect(recording).rejects.toMatchObject({
        message: 'Aucune quote-part ne s’applique à cette date.',
        status: 400,
      });
    });
  });
});

const command = (): CashPoolIncomeRecordingCommand => ({
  groupId: GROUP_ID,
  amountInCents: 125050,
  receivedAt: new Date('2026-10-01T09:30:00Z'),
  effectiveDate: new Date(2026, 8, 1),
});
