import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { StubGoogleIdTokenPort } from '../../../../__test__/app/application/auth/stub-google-id-token.port';
import { GoogleIdTokenPort } from '../../application/auth/google-id-token.port';
import type { DocumentedReimbursementDeclarationCommand } from '../../application/reimbursement/documented-reimbursement-declaration.port';
import { ApiClientError } from '../api/api-client.error';
import { apiAuthInterceptor } from '../api/api-auth.interceptor';
import { provideApiClient } from '../api/provide-api-client';
import { HttpDocumentedReimbursementDeclarationGateway } from './http-documented-reimbursement-declaration.gateway';

const GROUP_ID = '9d88fb48-4e2c-4a89-8b6b-509ed8f00b93';

describe('HttpDocumentedReimbursementDeclarationGateway', () => {
  let gateway: HttpDocumentedReimbursementDeclarationGateway;
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
    gateway = TestBed.inject(HttpDocumentedReimbursementDeclarationGateway);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  describe('declare', () => {
    it('declares the reimbursement through the generated API client', async () => {
      const declaration = gateway.declare(documentedReimbursementCommand());

      const request = httpTestingController.expectOne(
        `http://localhost:8080/api/groups/${GROUP_ID}/reimbursements/documented`,
      );
      expect({ method: request.request.method, body: request.request.body }).toEqual({
        method: 'POST',
        body: {
          receivedBy: 'alice@example.com',
          amountInCents: 4250,
          reimbursedAt: '2026-10-02T09:30:00.000Z',
          supportingDocumentUploadIntents: ['upload-intent-1', 'upload-intent-2'],
        },
      });
      request.flush(null, { status: 204, statusText: 'No Content' });

      await expect(declaration).resolves.toBeUndefined();
    });

    it('maps an unavailable upload intent to an ApiClientError', async () => {
      const declaration = gateway.declare(documentedReimbursementCommand());

      httpTestingController
        .expectOne(`http://localhost:8080/api/groups/${GROUP_ID}/reimbursements/documented`)
        .flush(
          { message: 'Le justificatif ne peut plus etre utilise.' },
          { status: 409, statusText: 'Conflict' },
        );

      await expect(declaration).rejects.toBeInstanceOf(ApiClientError);
      await expect(declaration).rejects.toMatchObject({
        message: 'Le justificatif ne peut plus etre utilise.',
        status: 409,
      });
    });
  });
});

const documentedReimbursementCommand = (): DocumentedReimbursementDeclarationCommand => ({
  groupId: GROUP_ID,
  receivedBy: 'alice@example.com',
  amountInCents: 4250,
  reimbursedAt: new Date('2026-10-02T09:30:00.000Z'),
  supportingDocumentUploadIntents: new Set(['upload-intent-1', 'upload-intent-2']),
});
