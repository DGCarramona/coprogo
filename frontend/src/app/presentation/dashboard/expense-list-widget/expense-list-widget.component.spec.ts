import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';

import { ExpenseListPort } from '../../../application/expense/expense-list.port';
import {
  ExpenseSupportingDocumentDeletionPort,
  type DeleteExpenseSupportingDocumentCommand,
} from '../../../application/supporting-document/expense-supporting-document-deletion.port';
import {
  ExpenseSupportingDocumentsPort,
  type ExpenseSupportingDocuments,
} from '../../../application/supporting-document/expense-supporting-documents.port';
import type { ExpenseSummary } from '../../../domain/expense/expense-summary';
import { ExpenseListWidgetComponent } from './expense-list-widget.component';

describe('ExpenseListWidgetComponent', () => {
  let expensesPort: StubExpenseListPort;
  let documentsPort: StubExpenseSupportingDocumentsPort;

  beforeEach(() => {
    expensesPort = new StubExpenseListPort();
    documentsPort = new StubExpenseSupportingDocumentsPort();
    TestBed.configureTestingModule({
      providers: [
        provideTanStackQuery(new QueryClient({ defaultOptions: { queries: { retry: false } } })),
        { provide: ExpenseListPort, useValue: expensesPort },
        { provide: ExpenseSupportingDocumentsPort, useValue: documentsPort },
        { provide: ExpenseSupportingDocumentDeletionPort, useValue: new StubDeletionPort() },
      ],
    });
  });

  describe('supporting documents details', () => {
    it('does not load documents before the user opens an expense section', async () => {
      const { fixture, host } = createFixture();
      fixture.detectChanges();
      await waitFor(() => host.querySelector('details') !== null);

      expect(documentsPort.requests).toEqual([]);
    });

    it('loads the opened expense documents and renders its reconstructed histories', async () => {
      documentsPort.result = documents({
        history: [
          document({ sourceUploadIntent: 'original', fileName: 'original.pdf' }),
          document({
            sourceUploadIntent: 'replacement',
            fileName: 'remplacement.pdf',
            replacesSourceUploadIntent: 'original',
          }),
        ],
      });
      const { fixture, host } = createFixture();
      fixture.detectChanges();
      await waitFor(() => host.querySelector('details') !== null);

      const details = host.querySelector<HTMLDetailsElement>('details')!;
      details.open = true;
      details.dispatchEvent(new Event('toggle'));
      fixture.detectChanges();

      await waitFor(() => documentsPort.requests.length === 1);
      await waitFor(() => host.textContent?.includes('remplacement.pdf') === true);

      expect(documentsPort.requests).toEqual([{ groupId: 'group-1', expenseId: 'expense-1' }]);
      expect(host.textContent).toContain('original.pdf');
      expect(host.textContent).toContain('remplacement.pdf');
    });
  });
});

const createFixture = (): {
  fixture: ComponentFixture<ExpenseListWidgetComponent>;
  host: HTMLElement;
} => {
  const fixture = TestBed.createComponent(ExpenseListWidgetComponent);
  fixture.componentInstance.groupId = 'group-1';
  return { fixture, host: fixture.nativeElement };
};

class StubExpenseListPort extends ExpenseListPort {
  override async listByGroup(groupId: string): Promise<readonly ExpenseSummary[]> {
    if (groupId !== 'group-1') return [];

    return [
      {
        id: 'expense-1',
        title: 'Courses',
        createdBy: 'alice@example.com',
        totalAmountInCents: 1500,
        createdAt: new Date('2026-09-20T09:15:00Z'),
        status: 'PROPOSED',
      },
    ];
  }
}

class StubExpenseSupportingDocumentsPort extends ExpenseSupportingDocumentsPort {
  result: ExpenseSupportingDocuments = documents();
  readonly requests: { groupId: string; expenseId: string }[] = [];

  override async listByExpense(
    groupId: string,
    expenseId: string,
  ): Promise<ExpenseSupportingDocuments> {
    this.requests.push({ groupId, expenseId });
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
