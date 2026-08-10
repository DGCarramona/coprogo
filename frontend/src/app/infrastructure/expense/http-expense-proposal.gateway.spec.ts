import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { StubGoogleIdTokenPort } from '../../../../__test__/app/application/auth/stub-google-id-token.port';
import { GoogleIdTokenPort } from '../../application/auth/google-id-token.port';
import type { ProposeEqualSplitExpenseCommand } from '../../application/expense/expense-proposal.port';
import { ApiClientError } from '../api/api-client.error';
import { provideApiClient } from '../api/provide-api-client';
import { HttpExpenseProposalGateway } from './http-expense-proposal.gateway';

describe('HttpExpenseProposalGateway', () => {
  let gateway: HttpExpenseProposalGateway;
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

    gateway = TestBed.inject(HttpExpenseProposalGateway);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('posts an equal split proposal and resolves after a no-content response', async () => {
    const command: ProposeEqualSplitExpenseCommand = {
      groupId: '9d88fb48-4e2c-4a89-8b6b-509ed8f00b93',
      title: 'Reparation toiture',
      totalAmountInCents: 12500,
      participants: new Set(['alice@example.com', 'bob@example.com']),
    };
    const proposal = gateway.proposeEqualSplit(command);

    const request = httpTestingController.expectOne(
      'http://localhost:8080/api/groups/9d88fb48-4e2c-4a89-8b6b-509ed8f00b93/expenses',
    );

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      title: 'Reparation toiture',
      totalAmountInCents: 12500,
      allocation: {
        type: 'EQUAL',
        participants: ['alice@example.com', 'bob@example.com'],
      },
    });

    request.flush(null, { status: 204, statusText: 'No Content' });

    await expect(proposal).resolves.toBeUndefined();
  });

  it('maps an API failure to an ApiClientError', async () => {
    const command: ProposeEqualSplitExpenseCommand = {
      groupId: '9d88fb48-4e2c-4a89-8b6b-509ed8f00b93',
      title: 'Reparation toiture',
      totalAmountInCents: 12500,
      participants: new Set(['alice@example.com']),
    };
    const proposal = gateway.proposeEqualSplit(command);

    const request = httpTestingController.expectOne(
      'http://localhost:8080/api/groups/9d88fb48-4e2c-4a89-8b6b-509ed8f00b93/expenses',
    );

    request.flush(
      { message: 'La depense ne peut pas etre proposee.' },
      { status: 409, statusText: 'Conflict' },
    );

    await expect(proposal).rejects.toBeInstanceOf(ApiClientError);
    await expect(proposal).rejects.toMatchObject({
      message: 'La depense ne peut pas etre proposee.',
      status: 409,
    });
  });
});
