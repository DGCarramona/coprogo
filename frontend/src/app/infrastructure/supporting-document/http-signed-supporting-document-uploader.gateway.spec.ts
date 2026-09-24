import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { StubGoogleIdTokenPort } from '../../../../__test__/app/application/auth/stub-google-id-token.port';
import { GoogleIdTokenPort } from '../../application/auth/google-id-token.port';
import { ApiClientError } from '../api/api-client.error';
import { provideApiClient } from '../api/provide-api-client';
import { provideCoprogoHttpClients } from '../http/named-http-clients';
import { HttpSignedSupportingDocumentUploaderGateway } from './http-signed-supporting-document-uploader.gateway';

describe('HttpSignedSupportingDocumentUploaderGateway', () => {
  let gateway: HttpSignedSupportingDocumentUploaderGateway;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideCoprogoHttpClients(),
        provideHttpClientTesting(),
        { provide: GoogleIdTokenPort, useClass: StubGoogleIdTokenPort },
        provideApiClient({ basePath: 'http://localhost:8080' }),
      ],
    });
    TestBed.inject(GoogleIdTokenPort).store('google-id-token');

    gateway = TestBed.inject(HttpSignedSupportingDocumentUploaderGateway);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  describe('upload', () => {
    it('puts the document directly with exactly the signed headers and no bearer token', async () => {
      const upload = gateway.upload(
        {
          intentId: 'e6371f49-61f3-4f2e-8d66-3af4201c8a3e',
          uploadUrl: 'https://documents.example/upload?signature=abc',
          requiredHeaders: {
            'Content-Type': 'application/pdf',
            'x-amz-checksum-sha256': 'checksum-base64',
          },
        },
        new Uint8Array([1, 2, 3]),
      );

      const request = httpTestingController.expectOne(
        'https://documents.example/upload?signature=abc',
      );
      expect(request.request.method).toBe('PUT');
      expect(request.request.headers.keys().sort()).toEqual([
        'Content-Type',
        'x-amz-checksum-sha256',
      ]);
      expect(request.request.headers.get('Content-Type')).toBe('application/pdf');
      expect(request.request.headers.get('x-amz-checksum-sha256')).toBe('checksum-base64');
      expect(request.request.headers.has('Authorization')).toBe(false);
      expect(request.request.body).toEqual(new Uint8Array([1, 2, 3]));

      request.flush(null, { status: 200, statusText: 'OK' });

      await expect(upload).resolves.toBeUndefined();
    });

    it('maps a direct upload failure without leaking the bearer token', async () => {
      const upload = gateway.upload(
        {
          intentId: 'e6371f49-61f3-4f2e-8d66-3af4201c8a3e',
          uploadUrl: 'https://documents.example/upload?signature=abc',
          requiredHeaders: { 'Content-Type': 'application/pdf' },
        },
        new Uint8Array([1, 2, 3]),
      );

      const request = httpTestingController.expectOne(
        'https://documents.example/upload?signature=abc',
      );
      expect(request.request.headers.has('Authorization')).toBe(false);
      request.flush({ message: 'Signature expiree.' }, { status: 403, statusText: 'Forbidden' });

      await expect(upload).rejects.toBeInstanceOf(ApiClientError);
      await expect(upload).rejects.toMatchObject({
        message: 'Signature expiree.',
        status: 403,
      });
    });
  });
});
