import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { StubGoogleIdTokenPort } from '../../../../__test__/app/application/auth/stub-google-id-token.port';
import { GoogleIdTokenPort } from '../../application/auth/google-id-token.port';
import type { ExpenseDetail } from '../../domain/expense/expense-detail';
import type { ExpenseDetailResponseDto } from '../api/generated';
import { ApiClientError } from '../api/api-client.error';
import { apiAuthInterceptor } from '../api/api-auth.interceptor';
import { provideApiClient } from '../api/provide-api-client';
import { HttpExpenseDetailGateway } from './http-expense-detail.gateway';

const GROUP_ID = '9d88fb48-4e2c-4a89-8b6b-509ed8f00b93';
const EXPENSE_ID = '73224b2f-c63a-475c-8976-38433b0a5685';

describe('HttpExpenseDetailGateway', () => {
  let gateway: HttpExpenseDetailGateway;
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
    gateway = TestBed.inject(HttpExpenseDetailGateway);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTestingController.verify());

  describe('get', () => {
    it('maps the expense and every participation status', async () => {
      const detail = gateway.get(GROUP_ID, EXPENSE_ID);

      expectDetailRequest().flush(expenseDetailResponse());

      const expected: ExpenseDetail = {
        id: EXPENSE_ID,
        title: 'Réfection du toit',
        createdBy: 'alice@example.com',
        canDecideParticipation: true,
        totalAmountInCents: 6000,
        createdAt: new Date('2026-07-01T10:00:00Z'),
        status: 'PROPOSED',
        participations: [
          { member: 'alice@example.com', amountInCents: 2000, status: 'APPROVED' },
          { member: 'bob@example.com', amountInCents: 2000, status: 'PENDING' },
          { member: 'charlie@example.com', amountInCents: 2000, status: 'REFUSED' },
        ],
      };
      await expect(detail).resolves.toEqual(expected);
    });

    it.each([
      {
        name: 'unknown expense status',
        response: expenseDetailResponse({ status: 'ARCHIVED' }),
        message: 'Statut de depense inconnu: ARCHIVED.',
      },
      {
        name: 'unknown participation status',
        response: expenseDetailResponse({
          participations: [{ member: 'alice@example.com', amountCents: 6000, status: 'IGNORED' }],
        }),
        message: 'Statut de participation inconnu: IGNORED.',
      },
      {
        name: 'invalid creation date',
        response: expenseDetailResponse({ createdAt: 'date-invalide' }),
        message: 'Date de creation de depense invalide: date-invalide.',
      },
    ])('rejects an $name', async ({ response, message }) => {
      const detail = gateway.get(GROUP_ID, EXPENSE_ID);

      expectDetailRequest().flush(response);

      await expect(detail).rejects.toThrow(message);
    });

    it('maps an API failure to an ApiClientError', async () => {
      const detail = gateway.get(GROUP_ID, EXPENSE_ID);

      expectDetailRequest().flush(
        { message: 'Dépense indisponible.' },
        { status: 503, statusText: 'Service Unavailable' },
      );

      await expect(detail).rejects.toBeInstanceOf(ApiClientError);
      await expect(detail).rejects.toMatchObject({ message: 'Dépense indisponible.', status: 503 });
    });
  });

  const expectDetailRequest = () => {
    const request = httpTestingController.expectOne(
      `http://localhost:8080/api/groups/${GROUP_ID}/expenses/${EXPENSE_ID}`,
    );
    expect(request.request.method).toBe('GET');
    return request;
  };
});

const expenseDetailResponse = (
  overrides: Partial<ExpenseDetailResponseDto> = {},
): ExpenseDetailResponseDto => ({
  id: EXPENSE_ID,
  title: 'Réfection du toit',
  createdBy: 'alice@example.com',
  canDecideParticipation: true,
  totalAmountCents: 6000,
  createdAt: '2026-07-01T10:00:00Z',
  status: 'PROPOSED',
  participations: [
    { member: 'alice@example.com', amountCents: 2000, status: 'APPROVED' },
    { member: 'bob@example.com', amountCents: 2000, status: 'PENDING' },
    { member: 'charlie@example.com', amountCents: 2000, status: 'REFUSED' },
  ],
  ...overrides,
});
