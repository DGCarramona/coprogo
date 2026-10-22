import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { provideRouter } from '@angular/router';

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
        provideRouter([]),
        { provide: ExpenseListPort, useValue: expensesPort },
        { provide: ExpenseSupportingDocumentsPort, useValue: documentsPort },
        { provide: ExpenseSupportingDocumentDeletionPort, useValue: new StubDeletionPort() },
      ],
    });
  });

  describe('loading feedback', () => {
    it('shows progress while expenses are loading', async () => {
      expensesPort.useDeferredResult();
      const { fixture, host } = createFixture();

      fixture.detectChanges();

      expect(host.querySelector('mat-progress-bar')).not.toBeNull();

      expensesPort.resolveDeferredResult();
      await waitFor(() => host.querySelector('mat-progress-bar') === null);
    });

    it('explains when the group has no expense', async () => {
      expensesPort.result = [];
      const { fixture, host } = createFixture();

      fixture.detectChanges();
      await waitFor(() => host.textContent?.includes('Aucune depense enregistree.'));

      expect(host.textContent).toContain('Aucune depense enregistree.');
    });

    it('lets the user retry after a loading failure', async () => {
      expensesPort.failure = new Error('Indisponible');
      const { fixture, host } = createFixture();
      fixture.detectChanges();
      await waitFor(() => host.querySelector('button') !== null);

      expensesPort.failure = null;
      host.querySelector<HTMLButtonElement>('button')?.click();
      await waitFor(() => host.textContent?.includes('Courses'));

      expect(expensesPort.requests).toEqual(['group-1', 'group-1']);
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

  describe('expense detail navigation', () => {
    it('links each expense to its group-scoped detail', async () => {
      const { fixture, host } = createFixture();
      fixture.detectChanges();
      await waitFor(() => host.querySelector('a') !== null);

      expect(host.querySelector('a')?.getAttribute('href')).toBe(
        '/groups/group-1/expenses/expense-1',
      );
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
  result: readonly ExpenseSummary[] = [
    {
      id: 'expense-1',
      title: 'Courses',
      createdBy: 'alice@example.com',
      totalAmountInCents: 1500,
      createdAt: new Date('2026-09-20T09:15:00Z'),
      status: 'PROPOSED',
    },
  ];
  failure: Error | null = null;
  readonly requests: string[] = [];
  private deferredResult: Deferred<readonly ExpenseSummary[]> | null = null;

  override async listByGroup(groupId: string): Promise<readonly ExpenseSummary[]> {
    this.requests.push(groupId);
    if (groupId !== 'group-1') return [];
    if (this.failure !== null) throw this.failure;
    if (this.deferredResult !== null) return this.deferredResult.promise;

    return this.result;
  }

  useDeferredResult(): void {
    this.deferredResult = deferred<readonly ExpenseSummary[]>();
  }

  resolveDeferredResult(): void {
    this.deferredResult?.resolve(this.result);
    this.deferredResult = null;
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

interface Deferred<T> {
  readonly promise: Promise<T>;
  readonly resolve: (value: T) => void;
}

const deferred = <T>(): Deferred<T> => {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((resolver) => {
    resolve = resolver;
  });
  return { promise, resolve };
};
