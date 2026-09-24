import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { StubGoogleIdTokenPort } from '../../../../__test__/app/application/auth/stub-google-id-token.port';
import { GoogleIdTokenPort } from '../../application/auth/google-id-token.port';
import { ApiClientError } from '../api/api-client.error';
import { apiAuthInterceptor } from '../api/api-auth.interceptor';
import { provideApiClient } from '../api/provide-api-client';
import { HttpSupportingDocumentUploadControlPlaneGateway } from './http-supporting-document-upload-control-plane.gateway';

describe('HttpSupportingDocumentUploadControlPlaneGateway', () => {
  let gateway: HttpSupportingDocumentUploadControlPlaneGateway;
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

    gateway = TestBed.inject(HttpSupportingDocumentUploadControlPlaneGateway);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  describe('start', () => {
    it('starts an upload and maps its signed target', async () => {
      const upload = gateway.start({
        groupId: 'c4276b6a-76f1-42d5-997a-bae590e8d137',
        fileName: 'facture.pdf',
        mediaType: 'application/pdf',
        sizeBytes: 42,
        sha256: 'checksum-base64',
      });

      const request = httpTestingController.expectOne(
        'http://localhost:8080/api/groups/c4276b6a-76f1-42d5-997a-bae590e8d137/supporting-document-uploads',
      );
      expect(request.request.method).toBe('POST');
      expect(request.request.body).toEqual({
        fileName: 'facture.pdf',
        mediaType: 'application/pdf',
        sizeBytes: 42,
        sha256: 'checksum-base64',
      });

      request.flush({
        intentId: 'e6371f49-61f3-4f2e-8d66-3af4201c8a3e',
        uploadUrl: 'https://documents.example/upload',
        requiredHeaders: { 'Content-Type': 'application/pdf' },
        expiresAt: '2026-09-24T13:00:00Z',
      });

      await expect(upload).resolves.toEqual({
        intentId: 'e6371f49-61f3-4f2e-8d66-3af4201c8a3e',
        uploadUrl: 'https://documents.example/upload',
        requiredHeaders: { 'Content-Type': 'application/pdf' },
      });
    });

    it('maps an API failure to an ApiClientError', async () => {
      const upload = gateway.start({
        groupId: 'c4276b6a-76f1-42d5-997a-bae590e8d137',
        fileName: 'facture.pdf',
        mediaType: 'application/pdf',
        sizeBytes: 42,
        sha256: 'checksum-base64',
      });

      httpTestingController
        .expectOne(
          'http://localhost:8080/api/groups/c4276b6a-76f1-42d5-997a-bae590e8d137/supporting-document-uploads',
        )
        .flush(
          { message: 'Le justificatif ne peut pas etre televerse.' },
          { status: 409, statusText: 'Conflict' },
        );

      await expect(upload).rejects.toMatchObject({
        message: 'Le justificatif ne peut pas etre televerse.',
        status: 409,
      } satisfies Partial<ApiClientError>);
    });
  });

  describe('confirm', () => {
    it('confirms the uploaded intent after a no-content response', async () => {
      const confirmation = gateway.confirm(
        'c4276b6a-76f1-42d5-997a-bae590e8d137',
        'e6371f49-61f3-4f2e-8d66-3af4201c8a3e',
      );

      const request = httpTestingController.expectOne(
        'http://localhost:8080/api/groups/c4276b6a-76f1-42d5-997a-bae590e8d137/supporting-document-uploads/e6371f49-61f3-4f2e-8d66-3af4201c8a3e/confirmation',
      );
      expect(request.request.method).toBe('POST');
      request.flush(null, { status: 204, statusText: 'No Content' });

      await expect(confirmation).resolves.toBeUndefined();
    });

    it('maps an API failure to an ApiClientError', async () => {
      const confirmation = gateway.confirm(
        'c4276b6a-76f1-42d5-997a-bae590e8d137',
        'e6371f49-61f3-4f2e-8d66-3af4201c8a3e',
      );

      httpTestingController
        .expectOne(
          'http://localhost:8080/api/groups/c4276b6a-76f1-42d5-997a-bae590e8d137/supporting-document-uploads/e6371f49-61f3-4f2e-8d66-3af4201c8a3e/confirmation',
        )
        .flush(
          { message: 'La confirmation du justificatif a echoue.' },
          { status: 422, statusText: 'Unprocessable Entity' },
        );

      await expect(confirmation).rejects.toBeInstanceOf(ApiClientError);
      await expect(confirmation).rejects.toMatchObject({
        message: 'La confirmation du justificatif a echoue.',
        status: 422,
      });
    });
  });
});
