import type { ExpenseSupportingDocument } from '../../../application/supporting-document/expense-supporting-documents.port';
import { reconstructSupportingDocumentHistoryChains } from './expense-supporting-document-history-chains';

describe('reconstructSupportingDocumentHistoryChains', () => {
  it('reconstructs multiple append-only histories in the received order', () => {
    expect(
      reconstructSupportingDocumentHistoryChains([
        document({ sourceUploadIntent: 'root-a' }),
        document({ sourceUploadIntent: 'root-b' }),
        document({ sourceUploadIntent: 'replacement-a', replacesSourceUploadIntent: 'root-a' }),
      ]),
    ).toEqual([
      {
        rootSourceUploadIntent: 'root-a',
        versions: [
          document({ sourceUploadIntent: 'root-a' }),
          document({ sourceUploadIntent: 'replacement-a', replacesSourceUploadIntent: 'root-a' }),
        ],
      },
      {
        rootSourceUploadIntent: 'root-b',
        versions: [document({ sourceUploadIntent: 'root-b' })],
      },
    ]);
  });

  it.each([
    [
      'a replacement source is absent',
      [document({ sourceUploadIntent: 'replacement', replacesSourceUploadIntent: 'missing' })],
      'Le justificatif replacement remplace une version introuvable: missing.',
    ],
    [
      'a replacement cycle exists',
      [
        document({ sourceUploadIntent: 'first', replacesSourceUploadIntent: 'second' }),
        document({ sourceUploadIntent: 'second', replacesSourceUploadIntent: 'first' }),
      ],
      'La chaine de justificatifs contient un cycle.',
    ],
    [
      'two versions replace the same source',
      [
        document({ sourceUploadIntent: 'root' }),
        document({ sourceUploadIntent: 'first', replacesSourceUploadIntent: 'root' }),
        document({ sourceUploadIntent: 'second', replacesSourceUploadIntent: 'root' }),
      ],
      'Une version de justificatif ne peut avoir qu un seul remplacement.',
    ],
  ])('fails fast when %s', (_description, history, message) => {
    expect(() => reconstructSupportingDocumentHistoryChains(history)).toThrow(message);
  });
});

const document = (overrides: Partial<ExpenseSupportingDocument>): ExpenseSupportingDocument => ({
  sourceUploadIntent: 'intent-1',
  fileName: 'facture.pdf',
  mediaType: 'application/pdf',
  sizeBytes: 1234,
  uploader: 'alice@example.com',
  attachedAt: new Date('2026-09-20T09:15:00Z'),
  replacesSourceUploadIntent: null,
  deletion: null,
  canDelete: false,
  downloadTarget: {
    url: 'https://documents.example.test/signed/document',
    expiresAt: new Date('2026-09-20T10:15:00Z'),
  },
  ...overrides,
});
