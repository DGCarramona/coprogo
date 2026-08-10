import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { StubGoogleIdTokenPort } from '../../../../__test__/app/application/auth/stub-google-id-token.port';
import { GoogleIdTokenPort } from '../../application/auth/google-id-token.port';
import { ApiClientError } from '../api/api-client.error';
import { provideApiClient } from '../api/provide-api-client';
import { HttpGroupFinancialDashboardGateway } from './http-group-financial-dashboard.gateway';

describe('HttpGroupFinancialDashboardGateway', () => {
  const groupId = '9d88fb48-4e2c-4a89-8b6b-509ed8f00b93';
  let gateway: HttpGroupFinancialDashboardGateway;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        {
          provide: GoogleIdTokenPort,
          useClass: StubGoogleIdTokenPort,
        },
        provideApiClient({ basePath: 'http://localhost:8080' }),
      ],
    });

    gateway = TestBed.inject(HttpGroupFinancialDashboardGateway);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('gets and combines the three financial views', async () => {
    const dashboard = gateway.get(groupId);

    const balancesRequest = httpTestingController.expectOne(
      `http://localhost:8080/api/groups/${groupId}/balances`,
    );
    const cashPoolBalanceRequest = httpTestingController.expectOne(
      `http://localhost:8080/api/groups/${groupId}/cash-pool/balance`,
    );
    const cashPoolSharesRequest = httpTestingController.expectOne(
      `http://localhost:8080/api/groups/${groupId}/cash-pool/shares`,
    );

    expect(balancesRequest.request.method).toBe('GET');
    expect(cashPoolBalanceRequest.request.method).toBe('GET');
    expect(cashPoolSharesRequest.request.method).toBe('GET');

    balancesRequest.flush({
      group: groupId,
      balances: [
        { member: 'alice@example.com', netAmountInCents: 3500 },
        { member: 'bob@example.com', netAmountInCents: -3500 },
      ],
    });
    cashPoolBalanceRequest.flush({ group: groupId, availableAmountInCents: 12500 });
    cashPoolSharesRequest.flush({
      group: groupId,
      shares: [
        { member: 'alice@example.com', amountInCents: 7500 },
        { member: 'bob@example.com', amountInCents: 5000 },
      ],
    });

    await expect(dashboard).resolves.toEqual({
      groupId,
      memberBalances: [
        { member: 'alice@example.com', netAmountInCents: 3500 },
        { member: 'bob@example.com', netAmountInCents: -3500 },
      ],
      cashPoolBalance: { availableAmountInCents: 12500 },
      cashPoolShares: [
        { member: 'alice@example.com', amountInCents: 7500 },
        { member: 'bob@example.com', amountInCents: 5000 },
      ],
    });
  });

  it('maps an API failure to an ApiClientError', async () => {
    const dashboard = gateway.get(groupId);

    const balancesRequest = httpTestingController.expectOne(
      `http://localhost:8080/api/groups/${groupId}/balances`,
    );
    const cashPoolBalanceRequest = httpTestingController.expectOne(
      `http://localhost:8080/api/groups/${groupId}/cash-pool/balance`,
    );
    const cashPoolSharesRequest = httpTestingController.expectOne(
      `http://localhost:8080/api/groups/${groupId}/cash-pool/shares`,
    );

    balancesRequest.flush({ group: groupId, balances: [] });
    cashPoolSharesRequest.flush({ group: groupId, shares: [] });
    cashPoolBalanceRequest.flush(
      { message: 'Le dashboard est indisponible.' },
      { status: 503, statusText: 'Service Unavailable' },
    );

    await expect(dashboard).rejects.toBeInstanceOf(ApiClientError);
    await expect(dashboard).rejects.toMatchObject({
      message: 'Le dashboard est indisponible.',
      status: 503,
    });
  });
});
