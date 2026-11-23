import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { StubGoogleIdTokenPort } from '../../../../__test__/app/application/auth/stub-google-id-token.port';
import { GoogleIdTokenPort } from '../../application/auth/google-id-token.port';
import { ApiClientError } from '../api/api-client.error';
import { apiAuthInterceptor } from '../api/api-auth.interceptor';
import { provideApiClient } from '../api/provide-api-client';
import { HttpMemberBalanceImpactHistoryGateway } from './http-member-balance-impact-history.gateway';

describe('HttpMemberBalanceImpactHistoryGateway', () => {
  const groupId = '9d88fb48-4e2c-4a89-8b6b-509ed8f00b93';
  const member = 'alice@example.com';
  let gateway: HttpMemberBalanceImpactHistoryGateway;
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
    gateway = TestBed.inject(HttpMemberBalanceImpactHistoryGateway);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTestingController.verify());

  describe('get', () => {
    it('maps the member balance-impact history returned by the generated client', async () => {
      const history = gateway.get(groupId, member);

      const request = httpTestingController.expectOne(
        `http://localhost:8080/api/groups/${groupId}/members/alice%40example.com/balance-impacts`,
      );
      expect(request.request.method).toBe('GET');
      request.flush({
        group: groupId,
        member,
        impacts: [
          {
            type: 'DIRECT_REIMBURSEMENT',
            effect: 'DEBT_REDUCTION',
            operationId: '06cb4461-38c4-41ee-bdc6-95ce5655c905',
            amountInCents: 1250,
            occurredAt: '2026-04-03T10:00:00Z',
          },
          {
            type: 'COMPENSATING_EXPENSE',
            effect: 'DEBT_REDUCTION',
            operationId: 'c11842b3-bf92-426b-b9f0-aa1fb3bc8f22',
            amountInCents: 500,
            occurredAt: '2026-04-04T10:00:00Z',
          },
          {
            type: 'CASH_POOL_WITHDRAWAL',
            effect: 'CREDIT_REDUCTION',
            operationId: 'ac14cf9b-b57e-461a-8aef-3c3869730426',
            amountInCents: 300,
            occurredAt: '2026-04-05T10:00:00Z',
          },
          {
            type: 'CASH_POOL_WITHDRAWAL',
            effect: 'DEBT_INCREASE',
            operationId: 'ac14cf9b-b57e-461a-8aef-3c3869730426',
            amountInCents: 100,
            occurredAt: '2026-04-05T10:00:00Z',
          },
        ],
      });

      await expect(history).resolves.toEqual({
        groupId,
        member,
        impacts: [
          {
            type: 'DIRECT_REIMBURSEMENT',
            effect: 'DEBT_REDUCTION',
            operationId: '06cb4461-38c4-41ee-bdc6-95ce5655c905',
            amountInCents: 1250,
            occurredAt: new Date('2026-04-03T10:00:00Z'),
          },
          {
            type: 'COMPENSATING_EXPENSE',
            effect: 'DEBT_REDUCTION',
            operationId: 'c11842b3-bf92-426b-b9f0-aa1fb3bc8f22',
            amountInCents: 500,
            occurredAt: new Date('2026-04-04T10:00:00Z'),
          },
          {
            type: 'CASH_POOL_WITHDRAWAL',
            effect: 'CREDIT_REDUCTION',
            operationId: 'ac14cf9b-b57e-461a-8aef-3c3869730426',
            amountInCents: 300,
            occurredAt: new Date('2026-04-05T10:00:00Z'),
          },
          {
            type: 'CASH_POOL_WITHDRAWAL',
            effect: 'DEBT_INCREASE',
            operationId: 'ac14cf9b-b57e-461a-8aef-3c3869730426',
            amountInCents: 100,
            occurredAt: new Date('2026-04-05T10:00:00Z'),
          },
        ],
      });
    });

    it('maps an API failure to an ApiClientError', async () => {
      const history = gateway.get(groupId, member);

      httpTestingController
        .expectOne(
          `http://localhost:8080/api/groups/${groupId}/members/alice%40example.com/balance-impacts`,
        )
        .flush(
          { message: 'Historique indisponible.' },
          { status: 503, statusText: 'Service Unavailable' },
        );

      await expect(history).rejects.toBeInstanceOf(ApiClientError);
      await expect(history).rejects.toMatchObject({
        message: 'Historique indisponible.',
        status: 503,
      });
    });

    it.each([
      {
        name: 'unknown operation type',
        impact: {
          type: 'UNKNOWN',
          operationId: 'operation-1',
          amountInCents: 100,
          occurredAt: '2026-04-03T10:00:00Z',
        },
        message: 'Type de remboursement inconnu: UNKNOWN.',
      },
      {
        name: 'invalid operation date',
        impact: {
          type: 'DIRECT_REIMBURSEMENT',
          effect: 'DEBT_REDUCTION',
          operationId: 'operation-1',
          amountInCents: 100,
          occurredAt: 'date-invalide',
        },
        message: 'Date d operation invalide: date-invalide.',
      },
      {
        name: 'unknown effect',
        impact: {
          type: 'CASH_POOL_WITHDRAWAL',
          effect: 'UNKNOWN',
          operationId: 'operation-1',
          amountInCents: 100,
          occurredAt: '2026-04-03T10:00:00Z',
        },
        message: 'Effet inconnu: UNKNOWN.',
      },
    ])('rejects an $name', async ({ impact, message }) => {
      const history = gateway.get(groupId, member);

      httpTestingController
        .expectOne(
          `http://localhost:8080/api/groups/${groupId}/members/alice%40example.com/balance-impacts`,
        )
        .flush({ group: groupId, member, impacts: [impact] });

      await expect(history).rejects.toThrow(message);
    });
  });
});
