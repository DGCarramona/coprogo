import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { StubGoogleIdTokenPort } from '../../../../__test__/app/application/auth/stub-google-id-token.port';
import { GoogleIdTokenPort } from '../../application/auth/google-id-token.port';
import { ApiClientError } from '../api/api-client.error';
import { apiAuthInterceptor } from '../api/api-auth.interceptor';
import { provideApiClient } from '../api/provide-api-client';
import { HttpExpenseParticipationDecisionGateway } from './http-expense-participation-decision.gateway';

const GROUP_ID = '9d88fb48-4e2c-4a89-8b6b-509ed8f00b93';
const EXPENSE_ID = '73224b2f-c63a-475c-8976-38433b0a5685';
const DECISION_URL =
  `http://localhost:8080/api/groups/${GROUP_ID}/expenses/${EXPENSE_ID}` +
  '/participation-decisions';

describe('HttpExpenseParticipationDecisionGateway', () => {
  let gateway: HttpExpenseParticipationDecisionGateway;
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
    gateway = TestBed.inject(HttpExpenseParticipationDecisionGateway);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTestingController.verify());

  describe('approve', () => {
    it('approves without sending a refusal reason', async () => {
      const approval = gateway.approve(GROUP_ID, EXPENSE_ID);

      const request = httpTestingController.expectOne(DECISION_URL);
      expect({ method: request.request.method, body: request.request.body }).toEqual({
        method: 'POST',
        body: { decision: 'APPROVE' },
      });
      request.flush(null, { status: 204, statusText: 'No Content' });

      await expect(approval).resolves.toBeUndefined();
    });
  });

  describe('refuse', () => {
    it('refuses with an optional reason', async () => {
      const refusal = gateway.refuse(GROUP_ID, EXPENSE_ID, 'Montant incorrect');

      const request = httpTestingController.expectOne(DECISION_URL);
      expect({ method: request.request.method, body: request.request.body }).toEqual({
        method: 'POST',
        body: { decision: 'REFUSE', reason: 'Montant incorrect' },
      });
      request.flush(null, { status: 204, statusText: 'No Content' });

      await expect(refusal).resolves.toBeUndefined();
    });

    it('maps an unavailable expense to an ApiClientError', async () => {
      const refusal = gateway.refuse(GROUP_ID, EXPENSE_ID, null);

      httpTestingController
        .expectOne(DECISION_URL)
        .flush({ message: 'Dépense indisponible.' }, { status: 409, statusText: 'Conflict' });

      await expect(refusal).rejects.toBeInstanceOf(ApiClientError);
      await expect(refusal).rejects.toMatchObject({
        message: 'Dépense indisponible.',
        status: 409,
      });
    });
  });
});
