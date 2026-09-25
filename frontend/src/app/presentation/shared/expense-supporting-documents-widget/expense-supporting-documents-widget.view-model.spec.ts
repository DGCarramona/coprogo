import { Injector } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';

import {
  ExpenseSupportingDocumentsPort,
  type ExpenseSupportingDocuments,
} from '../../../application/supporting-document/expense-supporting-documents.port';
import { expenseSupportingDocumentsQueryKey } from '../supporting-document/expense-supporting-documents-query-key';
import { ExpenseSupportingDocumentsWidgetViewModel } from './expense-supporting-documents-widget.view-model';

describe('ExpenseSupportingDocumentsWidgetViewModel', () => {
  let port: StubExpenseSupportingDocumentsPort;
  let queryClient: QueryClient;
  let injector: Injector;

  beforeEach(() => {
    port = new StubExpenseSupportingDocumentsPort();
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    TestBed.configureTestingModule({ providers: [provideTanStackQuery(queryClient)] });
    injector = TestBed.inject(Injector);
  });

  describe('initialize', () => {
    it('loads the history with the shared scoped cache key', async () => {
      const viewModel = createViewModel();

      viewModel.initialize('group-1', 'expense-1');
      await waitFor(() => viewModel.isReady());

      expect(port.requests).toEqual([{ groupId: 'group-1', expenseId: 'expense-1' }]);
      expect(
        queryClient.getQueryData(expenseSupportingDocumentsQueryKey('group-1', 'expense-1')),
      ).toEqual(documents());
    });

    it('exposes the document histories reconstructed from the append-only response', async () => {
      port.result = documents({
        history: [
          document({ sourceUploadIntent: 'first' }),
          document({ sourceUploadIntent: 'second', replacesSourceUploadIntent: 'first' }),
        ],
      });
      const viewModel = createViewModel();

      viewModel.initialize('group-1', 'expense-1');
      await waitFor(() => viewModel.isReady());

      expect(viewModel.histories()).toEqual([
        {
          rootSourceUploadIntent: 'first',
          versions: [
            document({ sourceUploadIntent: 'first' }),
            document({ sourceUploadIntent: 'second', replacesSourceUploadIntent: 'first' }),
          ],
        },
      ]);
    });

    it('exposes a loading failure', async () => {
      port.failure = new Error('Lecture indisponible');
      const viewModel = createViewModel();

      viewModel.initialize('group-1', 'expense-1');
      await waitFor(() => viewModel.hasLoadError());

      expect(viewModel.errorMessage()).toBe('Lecture indisponible');
    });
  });

  describe('retry', () => {
    it('retries the same expense history after a failure', async () => {
      port.failure = new Error('Premier echec');
      const viewModel = createViewModel();
      viewModel.initialize('group-1', 'expense-1');
      await waitFor(() => viewModel.hasLoadError());

      port.failure = null;
      viewModel.retry();
      await waitFor(() => viewModel.isReady());

      expect(port.requests).toEqual([
        { groupId: 'group-1', expenseId: 'expense-1' },
        { groupId: 'group-1', expenseId: 'expense-1' },
      ]);
    });
  });

  const createViewModel = () => new ExpenseSupportingDocumentsWidgetViewModel(port, injector);
});

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
