import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { StubGoogleIdTokenPort } from '../../../../__test__/app/application/auth/stub-google-id-token.port';
import { GoogleIdTokenPort } from '../../application/auth/google-id-token.port';
import type { ExpenseSupportingDocumentsResponseDto } from '../api/generated';
import { ApiClientError } from '../api/api-client.error';
import { apiAuthInterceptor } from '../api/api-auth.interceptor';
import { provideApiClient } from '../api/provide-api-client';
import { HttpExpenseSupportingDocumentsGateway } from './http-expense-supporting-documents.gateway';

const GROUP_ID = '9d88fb48-4e2c-4a89-8b6b-509ed8f00b93';
const EXPENSE_ID = 'd6b3e2ef-682b-4dfb-9f94-8e0d2e31f1b4';

describe('HttpExpenseSupportingDocumentsGateway', () => {
  let gateway: HttpExpenseSupportingDocumentsGateway;
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

    gateway = TestBed.inject(HttpExpenseSupportingDocumentsGateway);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTestingController.verify());

  describe('listByExpense', () => {
    it('maps current and historical documents without leaking generated DTOs', async () => {
      const documents = gateway.listByExpense(GROUP_ID, EXPENSE_ID);

      expectListRequest().flush(response());

      await expect(documents).resolves.toEqual({
        current: [
          {
            sourceUploadIntent: 'intent-current',
            fileName: 'facture.pdf',
            mediaType: 'application/pdf',
            sizeBytes: 1234,
            uploader: 'alice@example.com',
            attachedAt: new Date('2026-09-20T09:15:00.000Z'),
            replacesSourceUploadIntent: 'intent-original',
            deletion: null,
            canDelete: true,
            downloadTarget: {
              url: 'https://storage.example.test/current?signature=secret',
              expiresAt: new Date('2026-09-20T10:15:00.000Z'),
            },
          },
        ],
        history: [
          {
            sourceUploadIntent: 'intent-original',
            fileName: 'facture-originale.pdf',
            mediaType: 'application/pdf',
            sizeBytes: 1000,
            uploader: 'alice@example.com',
            attachedAt: new Date('2026-09-19T09:15:00.000Z'),
            replacesSourceUploadIntent: null,
            deletion: null,
            canDelete: false,
            downloadTarget: {
              url: 'https://storage.example.test/original?signature=secret',
              expiresAt: new Date('2026-09-19T10:15:00.000Z'),
            },
          },
          {
            sourceUploadIntent: 'intent-current',
            fileName: 'facture.pdf',
            mediaType: 'application/pdf',
            sizeBytes: 1234,
            uploader: 'alice@example.com',
            attachedAt: new Date('2026-09-20T09:15:00.000Z'),
            replacesSourceUploadIntent: 'intent-original',
            deletion: null,
            canDelete: true,
            downloadTarget: {
              url: 'https://storage.example.test/current?signature=secret',
              expiresAt: new Date('2026-09-20T10:15:00.000Z'),
            },
          },
        ],
      });
    });

    it('maps the deletion audit without leaking the generated DTO', async () => {
      const documents = gateway.listByExpense(GROUP_ID, EXPENSE_ID);

      expectListRequest().flush(
        response({
          current: [],
          history: [
            documentResponse({
              deletion: {
                deletedBy: 'bob@example.com',
                deletedAt: '2026-09-21T11:30:00.000Z',
              },
              canDelete: false,
            }),
          ],
        }),
      );

      await expect(documents).resolves.toEqual({
        current: [],
        history: [
          {
            sourceUploadIntent: 'intent-current',
            fileName: 'facture.pdf',
            mediaType: 'application/pdf',
            sizeBytes: 1234,
            uploader: 'alice@example.com',
            attachedAt: new Date('2026-09-20T09:15:00.000Z'),
            replacesSourceUploadIntent: 'intent-original',
            deletion: {
              deletedBy: 'bob@example.com',
              deletedAt: new Date('2026-09-21T11:30:00.000Z'),
            },
            canDelete: false,
            downloadTarget: {
              url: 'https://storage.example.test/current?signature=secret',
              expiresAt: new Date('2026-09-20T10:15:00.000Z'),
            },
          },
        ],
      });
    });

    it.each([
      [
        'attachment date',
        documentResponse({ attachedAt: 'not-an-attachment-date' }),
        'Date d ajout du justificatif invalide: not-an-attachment-date.',
      ],
      [
        'deletion date',
        documentResponse({
          deletion: { deletedBy: 'bob@example.com', deletedAt: 'not-a-deletion-date' },
        }),
        'Date de retrait du justificatif invalide: not-a-deletion-date.',
      ],
      [
        'download expiration date',
        documentResponse({
          download: {
            url: 'https://storage.example.test/document?signature=secret',
            expiresAt: 'not-an-expiration-date',
          },
        }),
        'Date d expiration du lien invalide: not-an-expiration-date.',
      ],
    ])('fails fast when the %s is invalid', async (_description, document, message) => {
      const documents = gateway.listByExpense(GROUP_ID, EXPENSE_ID);

      expectListRequest().flush(response({ current: [], history: [document] }));

      await expect(documents).rejects.toThrow(message);
    });

    it('maps API errors to a French ApiClientError', async () => {
      const documents = gateway.listByExpense(GROUP_ID, EXPENSE_ID);

      expectListRequest().flush(
        { message: 'Les justificatifs sont indisponibles.' },
        { status: 503, statusText: 'Service Unavailable' },
      );

      await expect(documents).rejects.toBeInstanceOf(ApiClientError);
      await expect(documents).rejects.toMatchObject({
        message: 'Les justificatifs sont indisponibles.',
        status: 503,
      });
    });
  });

  const expectListRequest = () => {
    const request = httpTestingController.expectOne(
      `http://localhost:8080/api/groups/${GROUP_ID}/expenses/${EXPENSE_ID}/supporting-documents`,
    );
    expect(request.request.method).toBe('GET');
    return request;
  };
});

const response = (
  overrides: Partial<ExpenseSupportingDocumentsResponseDto> = {},
): ExpenseSupportingDocumentsResponseDto => ({
  current: [
    documentResponse({
      sourceUploadIntent: 'intent-current',
      replacesSourceUploadIntent: 'intent-original',
      canDelete: true,
    }),
  ],
  history: [
    documentResponse({
      sourceUploadIntent: 'intent-original',
      fileName: 'facture-originale.pdf',
      sizeBytes: 1000,
      attachedAt: '2026-09-19T09:15:00.000Z',
      replacesSourceUploadIntent: null,
      canDelete: false,
      download: {
        url: 'https://storage.example.test/original?signature=secret',
        expiresAt: '2026-09-19T10:15:00.000Z',
      },
    }),
    documentResponse({
      sourceUploadIntent: 'intent-current',
      replacesSourceUploadIntent: 'intent-original',
      canDelete: true,
    }),
  ],
  ...overrides,
});

const documentResponse = (overrides: Record<string, unknown> = {}) => ({
  sourceUploadIntent: 'intent-current',
  fileName: 'facture.pdf',
  mediaType: 'application/pdf',
  sizeBytes: 1234,
  uploader: 'alice@example.com',
  attachedAt: '2026-09-20T09:15:00.000Z',
  replacesSourceUploadIntent: 'intent-original',
  deletion: null,
  canDelete: true,
  download: {
    url: 'https://storage.example.test/current?signature=secret',
    expiresAt: '2026-09-20T10:15:00.000Z',
  },
  ...overrides,
});
