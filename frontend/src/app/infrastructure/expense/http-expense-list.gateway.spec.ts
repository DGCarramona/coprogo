import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { StubGoogleIdTokenPort } from '../../../../__test__/app/application/auth/stub-google-id-token.port';
import { GoogleIdTokenPort } from '../../application/auth/google-id-token.port';
import type { ExpenseSummary } from '../../domain/expense/expense-summary';
import type { ExpenseResponseDto } from '../api/generated';
import { ApiClientError } from '../api/api-client.error';
import { provideApiClient } from '../api/provide-api-client';
import { HttpExpenseListGateway } from './http-expense-list.gateway';

const GROUP_ID = '9d88fb48-4e2c-4a89-8b6b-509ed8f00b93';

describe('HttpExpenseListGateway', () => {
  let gateway: HttpExpenseListGateway;
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

    gateway = TestBed.inject(HttpExpenseListGateway);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('maps expense responses to frontend expense summaries', async () => {
    const expenses = gateway.listByGroup(GROUP_ID);

    const request = expectListRequest();
    request.flush([expenseResponse()]);

    const expected: readonly ExpenseSummary[] = [
      {
        id: 'expense-1',
        title: 'Courses',
        createdBy: 'alice@example.com',
        totalAmountInCents: 1500,
        createdAt: new Date('2026-06-01T10:00:00.000Z'),
        status: 'ACCEPTED',
      },
    ];
    await expect(expenses).resolves.toEqual(expected);
  });

  it('rejects an unknown expense status', async () => {
    const expenses = gateway.listByGroup(GROUP_ID);

    const request = expectListRequest();
    request.flush([expenseResponse({ status: 'ARCHIVED' })]);

    await expect(expenses).rejects.toThrow('Statut de depense inconnu: ARCHIVED.');
  });

  it('rejects an invalid creation date', async () => {
    const expenses = gateway.listByGroup(GROUP_ID);

    const request = expectListRequest();
    request.flush([expenseResponse({ createdAt: 'date-invalide' })]);

    await expect(expenses).rejects.toThrow('Date de creation de depense invalide: date-invalide.');
  });

  it('maps an API failure to an ApiClientError', async () => {
    const expenses = gateway.listByGroup(GROUP_ID);

    const request = expectListRequest();
    request.flush(
      { message: 'Les depenses du groupe sont indisponibles.' },
      { status: 503, statusText: 'Service Unavailable' },
    );

    await expect(expenses).rejects.toBeInstanceOf(ApiClientError);
    await expect(expenses).rejects.toMatchObject({
      message: 'Les depenses du groupe sont indisponibles.',
      status: 503,
    });
  });

  const expectListRequest = () => {
    const request = httpTestingController.expectOne(
      `http://localhost:8080/api/groups/${GROUP_ID}/expenses`,
    );
    expect(request.request.method).toBe('GET');

    return request;
  };
});

const expenseResponse = (overrides: Partial<ExpenseResponseDto> = {}): ExpenseResponseDto => ({
  id: 'expense-1',
  title: 'Courses',
  createdBy: 'alice@example.com',
  totalAmountCents: 1500,
  createdAt: '2026-06-01T10:00:00.000Z',
  status: 'ACCEPTED',
  ...overrides,
});
