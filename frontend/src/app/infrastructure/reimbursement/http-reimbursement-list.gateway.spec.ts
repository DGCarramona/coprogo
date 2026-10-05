import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { StubGoogleIdTokenPort } from '../../../../__test__/app/application/auth/stub-google-id-token.port';
import { GoogleIdTokenPort } from '../../application/auth/google-id-token.port';
import type { ReimbursementSummary } from '../../domain/reimbursement/reimbursement-summary';
import type { ReimbursementResponseDto } from '../api/generated';
import { ApiClientError } from '../api/api-client.error';
import { apiAuthInterceptor } from '../api/api-auth.interceptor';
import { provideApiClient } from '../api/provide-api-client';
import { HttpReimbursementListGateway } from './http-reimbursement-list.gateway';

const GROUP_ID = '9d88fb48-4e2c-4a89-8b6b-509ed8f00b93';

describe('HttpReimbursementListGateway', () => {
  let gateway: HttpReimbursementListGateway;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([apiAuthInterceptor])),
        provideHttpClientTesting(),
        {
          provide: GoogleIdTokenPort,
          useClass: StubGoogleIdTokenPort,
        },
        provideApiClient({ basePath: 'http://localhost:8080' }),
      ],
    });

    gateway = TestBed.inject(HttpReimbursementListGateway);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  describe('listByGroup', () => {
    it('maps reimbursement responses and their supporting documents', async () => {
      const reimbursements = gateway.listByGroup(GROUP_ID);

      expectListRequest().flush([reimbursementResponse()]);

      const expected: readonly ReimbursementSummary[] = [
        {
          id: 'reimbursement-1',
          paidBy: 'bob@example.com',
          receivedBy: 'alice@example.com',
          amountInCents: 2500,
          reimbursedAt: new Date('2026-10-01T09:00:00.000Z'),
          declaredBy: 'bob@example.com',
          declaredAt: new Date('2026-10-01T10:00:00.000Z'),
          status: 'PENDING_REVIEW',
          decidedAt: null,
          rejectionReason: null,
          documents: [
            {
              sourceUploadIntent: 'upload-1',
              fileName: 'preuve.pdf',
              mediaType: 'application/pdf',
              sizeBytes: 1234,
              uploader: 'bob@example.com',
              attachedAt: new Date('2026-10-01T10:00:00.000Z'),
              downloadTarget: {
                url: 'https://documents.example/preuve.pdf',
                expiresAt: new Date('2026-10-01T10:05:00.000Z'),
              },
            },
          ],
        },
      ];
      await expect(reimbursements).resolves.toEqual(expected);
    });

    it.each([
      {
        name: 'pending review',
        response: reimbursementResponse(),
        expected: { status: 'PENDING_REVIEW', decidedAt: null, rejectionReason: null },
      },
      {
        name: 'acceptance',
        response: reimbursementResponse({
          status: 'ACCEPTED',
          decidedAt: '2026-10-01T11:00:00.000Z',
        }),
        expected: {
          status: 'ACCEPTED',
          decidedAt: new Date('2026-10-01T11:00:00.000Z'),
          rejectionReason: null,
        },
      },
      {
        name: 'rejection',
        response: reimbursementResponse({
          status: 'REJECTED',
          decidedAt: '2026-10-01T11:00:00.000Z',
          rejectionReason: 'Le montant ne correspond pas.',
        }),
        expected: {
          status: 'REJECTED',
          decidedAt: new Date('2026-10-01T11:00:00.000Z'),
          rejectionReason: 'Le montant ne correspond pas.',
        },
      },
    ] as const)(
      'maps the $name decision',
      async ({ response, expected }) => {
        const reimbursements = gateway.listByGroup(GROUP_ID);

        expectListRequest().flush([response]);

        await expect(reimbursements).resolves.toMatchObject([expected]);
      },
    );

    it('rejects an unknown reimbursement status', async () => {
      const reimbursements = gateway.listByGroup(GROUP_ID);

      expectListRequest().flush([{ ...reimbursementResponse(), status: 'CANCELLED' }]);

      await expect(reimbursements).rejects.toThrow(
        'Statut de remboursement inconnu: CANCELLED.',
      );
    });

    it.each([
      {
        name: 'reimbursement date',
        response: reimbursementResponse({ reimbursedAt: 'date-invalide' }),
        message: 'Date de remboursement invalide: date-invalide.',
      },
      {
        name: 'declaration date',
        response: reimbursementResponse({ declaredAt: 'date-invalide' }),
        message: 'Date de declaration du remboursement invalide: date-invalide.',
      },
      {
        name: 'decision date',
        response: reimbursementResponse({ status: 'ACCEPTED', decidedAt: 'date-invalide' }),
        message: 'Date de decision du remboursement invalide: date-invalide.',
      },
      {
        name: 'document attachment date',
        response: reimbursementResponse({
          documents: [supportingDocumentResponse({ attachedAt: 'date-invalide' })],
        }),
        message: 'Date d ajout du justificatif invalide: date-invalide.',
      },
      {
        name: 'download expiration date',
        response: reimbursementResponse({
          documents: [
            supportingDocumentResponse({
              download: {
                url: 'https://documents.example/preuve.pdf',
                expiresAt: 'date-invalide',
              },
            }),
          ],
        }),
        message: 'Date d expiration du telechargement invalide: date-invalide.',
      },
    ])('rejects an invalid $name', async ({ response, message }) => {
      const reimbursements = gateway.listByGroup(GROUP_ID);

      expectListRequest().flush([response]);

      await expect(reimbursements).rejects.toThrow(message);
    });

    it('maps an API failure to an ApiClientError', async () => {
      const reimbursements = gateway.listByGroup(GROUP_ID);

      expectListRequest().flush(
        { message: 'Les remboursements sont indisponibles.' },
        { status: 503, statusText: 'Service Unavailable' },
      );

      await expect(reimbursements).rejects.toBeInstanceOf(ApiClientError);
      await expect(reimbursements).rejects.toMatchObject({
        message: 'Les remboursements sont indisponibles.',
        status: 503,
      });
    });
  });

  const expectListRequest = () => {
    const request = httpTestingController.expectOne(
      `http://localhost:8080/api/groups/${GROUP_ID}/reimbursements`,
    );
    expect(request.request.method).toBe('GET');

    return request;
  };
});

const reimbursementResponse = (
  overrides: Partial<ReimbursementResponseDto> = {},
): ReimbursementResponseDto => ({
  id: 'reimbursement-1',
  paidBy: 'bob@example.com',
  receivedBy: 'alice@example.com',
  amountInCents: 2500,
  reimbursedAt: '2026-10-01T09:00:00.000Z',
  declaredBy: 'bob@example.com',
  declaredAt: '2026-10-01T10:00:00.000Z',
  status: 'PENDING_REVIEW',
  decidedAt: null,
  rejectionReason: null,
  documents: [supportingDocumentResponse()],
  ...overrides,
});

const supportingDocumentResponse = (
  overrides: Partial<ReimbursementResponseDto['documents'][number]> = {},
): ReimbursementResponseDto['documents'][number] => ({
  sourceUploadIntent: 'upload-1',
  fileName: 'preuve.pdf',
  mediaType: 'application/pdf',
  sizeBytes: 1234,
  uploader: 'bob@example.com',
  attachedAt: '2026-10-01T10:00:00.000Z',
  download: {
    url: 'https://documents.example/preuve.pdf',
    expiresAt: '2026-10-01T10:05:00.000Z',
  },
  ...overrides,
});
