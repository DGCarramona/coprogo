import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { StubGoogleIdTokenPort } from '../../../../__test__/app/application/auth/stub-google-id-token.port';
import { GoogleIdTokenPort } from '../../application/auth/google-id-token.port';
import { ApiClientError } from '../api/api-client.error';
import { apiAuthInterceptor } from '../api/api-auth.interceptor';
import { provideApiClient } from '../api/provide-api-client';
import { HttpExpenseSupportingDocumentDeletionGateway } from './http-expense-supporting-document-deletion.gateway';

describe('HttpExpenseSupportingDocumentDeletionGateway', () => {
  let gateway: HttpExpenseSupportingDocumentDeletionGateway;
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

    gateway = TestBed.inject(HttpExpenseSupportingDocumentDeletionGateway);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  describe('delete', () => {
    it('deletes the targeted supporting document and resolves after a no-content response', async () => {
      const deletion = gateway.delete({
        groupId: 'c4276b6a-76f1-42d5-997a-bae590e8d137',
        expenseId: 'a3fe73e7-648d-494f-bccd-5f9562e2e657',
        sourceUploadIntent: 'e6371f49-61f3-4f2e-8d66-3af4201c8a3e',
      });

      const request = httpTestingController.expectOne(
        'http://localhost:8080/api/groups/c4276b6a-76f1-42d5-997a-bae590e8d137/expenses/a3fe73e7-648d-494f-bccd-5f9562e2e657/supporting-documents/e6371f49-61f3-4f2e-8d66-3af4201c8a3e',
      );
      expect(request.request.method).toBe('DELETE');
      expect(request.request.body).toBeNull();
      request.flush(null, { status: 204, statusText: 'No Content' });

      await expect(deletion).resolves.toBeUndefined();
    });

    it('maps an API failure to an ApiClientError', async () => {
      const deletion = gateway.delete({
        groupId: 'c4276b6a-76f1-42d5-997a-bae590e8d137',
        expenseId: 'a3fe73e7-648d-494f-bccd-5f9562e2e657',
        sourceUploadIntent: 'e6371f49-61f3-4f2e-8d66-3af4201c8a3e',
      });

      httpTestingController
        .expectOne(
          'http://localhost:8080/api/groups/c4276b6a-76f1-42d5-997a-bae590e8d137/expenses/a3fe73e7-648d-494f-bccd-5f9562e2e657/supporting-documents/e6371f49-61f3-4f2e-8d66-3af4201c8a3e',
        )
        .flush(
          { message: 'Le justificatif ne peut pas etre retire.' },
          { status: 409, statusText: 'Conflict' },
        );

      await expect(deletion).rejects.toBeInstanceOf(ApiClientError);
      await expect(deletion).rejects.toMatchObject({
        message: 'Le justificatif ne peut pas etre retire.',
        status: 409,
      });
    });
  });
});
