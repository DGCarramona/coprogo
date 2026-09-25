import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';

import {
  ExpenseSupportingDocumentDeletionPort,
  type DeleteExpenseSupportingDocumentCommand,
} from '../../../application/supporting-document/expense-supporting-document-deletion.port';
import {
  ExpenseSupportingDocumentsPort,
  type ExpenseSupportingDocuments,
} from '../../../application/supporting-document/expense-supporting-documents.port';
import { ExpenseSupportingDocumentsWidgetComponent } from './expense-supporting-documents-widget.component';

describe('ExpenseSupportingDocumentsWidgetComponent', () => {
  let documentsPort: StubExpenseSupportingDocumentsPort;

  beforeEach(() => {
    documentsPort = new StubExpenseSupportingDocumentsPort();
    TestBed.configureTestingModule({
      providers: [
        provideTanStackQuery(new QueryClient({ defaultOptions: { queries: { retry: false } } })),
        { provide: ExpenseSupportingDocumentsPort, useValue: documentsPort },
        { provide: ExpenseSupportingDocumentDeletionPort, useValue: new StubDeletionPort() },
      ],
    });
  });

  describe('visible states', () => {
    it('explains when the expense has no supporting document', async () => {
      const { fixture, host } = createFixture();
      fixture.detectChanges();

      await waitFor(() => host.textContent?.includes('Aucun justificatif') === true);

      expect(host.textContent).toContain('Aucun justificatif n’a été ajouté.');
    });

    it('renders each reconstructed history', async () => {
      documentsPort.result = documents({
        history: [
          document({ sourceUploadIntent: 'first', fileName: 'premiere.pdf' }),
          document({
            sourceUploadIntent: 'second',
            fileName: 'seconde.pdf',
            replacesSourceUploadIntent: 'first',
          }),
          document({ sourceUploadIntent: 'other', fileName: 'autre.pdf' }),
        ],
      });
      const { fixture, host } = createFixture();
      fixture.detectChanges();

      await waitFor(() => host.querySelectorAll('app-supporting-document-history').length === 2);

      expect(
        [...host.querySelectorAll('app-supporting-document-history')].map((history) =>
          history.textContent?.replace(/\s+/g, ' ').trim(),
        ),
      ).toEqual([expect.stringContaining('premiere.pdf'), expect.stringContaining('autre.pdf')]);
      expect(host.textContent).toContain('seconde.pdf');
    });

    it('shows a retry action after a loading error', async () => {
      documentsPort.failure = new Error('Lecture indisponible');
      const { fixture, host } = createFixture();
      fixture.detectChanges();

      await waitFor(() => host.querySelector('[role="alert"]') !== null);

      expect(host.querySelector('[role="alert"]')?.textContent?.trim()).toBe(
        'Lecture indisponible',
      );
      expect(host.querySelector<HTMLButtonElement>('button')?.textContent?.trim()).toBe(
        'Réessayer',
      );

      documentsPort.failure = null;
      host.querySelector<HTMLButtonElement>('button')?.click();
      await waitFor(() => host.textContent?.includes('Aucun justificatif') === true);

      expect(documentsPort.requests).toEqual([
        { groupId: 'group-1', expenseId: 'expense-1' },
        { groupId: 'group-1', expenseId: 'expense-1' },
      ]);
    });
  });
});

const createFixture = (): {
  fixture: ComponentFixture<ExpenseSupportingDocumentsWidgetComponent>;
  host: HTMLElement;
} => {
  const fixture = TestBed.createComponent(ExpenseSupportingDocumentsWidgetComponent);
  fixture.componentRef.setInput('groupId', 'group-1');
  fixture.componentRef.setInput('expenseId', 'expense-1');
  return { fixture, host: fixture.nativeElement };
};

class StubExpenseSupportingDocumentsPort extends ExpenseSupportingDocumentsPort {
  result: ExpenseSupportingDocuments = documents();
  failure: Error | null = null;
  readonly requests: { groupId: string; expenseId: string }[] = [];

  override async listByExpense(
    groupId: string,
    expenseId: string,
  ): Promise<ExpenseSupportingDocuments> {
    this.requests.push({ groupId, expenseId });
    if (this.failure !== null) throw this.failure;
    return this.result;
  }
}

class StubDeletionPort extends ExpenseSupportingDocumentDeletionPort {
  override delete(command: DeleteExpenseSupportingDocumentCommand): Promise<void> {
    void command;
    return Promise.resolve();
  }
}

const documents = (
  overrides: Partial<ExpenseSupportingDocuments> = {},
): ExpenseSupportingDocuments => ({
  current: [],
  history: [],
  ...overrides,
});

const document = (overrides: Record<string, unknown>) => ({
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

const waitFor = async (condition: () => boolean): Promise<void> => {
  for (let attempt = 0; attempt < 50; attempt += 1) {
    TestBed.tick();
    if (condition()) return;
    await new Promise((resolve) => setTimeout(resolve));
  }

  throw new Error('La condition attendue n a pas ete atteinte.');
};
