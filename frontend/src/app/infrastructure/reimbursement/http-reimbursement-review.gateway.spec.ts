import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { StubGoogleIdTokenPort } from '../../../../__test__/app/application/auth/stub-google-id-token.port';
import { GoogleIdTokenPort } from '../../application/auth/google-id-token.port';
import { ApiClientError } from '../api/api-client.error';
import { apiAuthInterceptor } from '../api/api-auth.interceptor';
import { provideApiClient } from '../api/provide-api-client';
import { HttpReimbursementReviewGateway } from './http-reimbursement-review.gateway';

const GROUP_ID = '9d88fb48-4e2c-4a89-8b6b-509ed8f00b93';
const REIMBURSEMENT_ID = 'e60c8d67-8766-4720-9b91-d1a7fd68280c';
const REVIEW_URL = `http://localhost:8080/api/groups/${GROUP_ID}/reimbursements/${REIMBURSEMENT_ID}`;

describe('HttpReimbursementReviewGateway', () => {
  let gateway: HttpReimbursementReviewGateway;
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
    gateway = TestBed.inject(HttpReimbursementReviewGateway);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTestingController.verify());

  describe('accept', () => {
    it('accepts the reimbursement through the generated API client', async () => {
      const acceptance = gateway.accept(GROUP_ID, REIMBURSEMENT_ID);

      const request = httpTestingController.expectOne(`${REVIEW_URL}/acceptance`);
      expect({ method: request.request.method, body: request.request.body }).toEqual({
        method: 'POST',
        body: null,
      });
      request.flush(null, { status: 204, statusText: 'No Content' });

      await expect(acceptance).resolves.toBeUndefined();
    });
  });

  describe('reject', () => {
    it('rejects the reimbursement with an optional reason', async () => {
      const rejection = gateway.reject(GROUP_ID, REIMBURSEMENT_ID, 'Montant incorrect');

      const request = httpTestingController.expectOne(`${REVIEW_URL}/rejection`);
      expect({ method: request.request.method, body: request.request.body }).toEqual({
        method: 'POST',
        body: { reason: 'Montant incorrect' },
      });
      request.flush(null, { status: 204, statusText: 'No Content' });

      await expect(rejection).resolves.toBeUndefined();
    });

    it('maps an unavailable reimbursement to an ApiClientError', async () => {
      const rejection = gateway.reject(GROUP_ID, REIMBURSEMENT_ID, null);

      httpTestingController
        .expectOne(`${REVIEW_URL}/rejection`)
        .flush(
          { message: 'Remboursement indisponible.' },
          { status: 404, statusText: 'Not Found' },
        );

      await expect(rejection).rejects.toBeInstanceOf(ApiClientError);
      await expect(rejection).rejects.toMatchObject({
        message: 'Remboursement indisponible.',
        status: 404,
      });
    });
  });
});
