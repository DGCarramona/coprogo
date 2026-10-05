import { Injector } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';

import {
  ExpenseSupportingDocumentDeletionPort,
  type DeleteExpenseSupportingDocumentCommand,
} from '../../../application/supporting-document/expense-supporting-document-deletion.port';
import { expenseSupportingDocumentsQueryKey } from '../supporting-document/expense-supporting-documents-query-key';
import { SupportingDocumentHistoryViewModel } from './supporting-document-history.view-model';

describe('SupportingDocumentHistoryViewModel', () => {
  let deletionPort: StubExpenseSupportingDocumentDeletionPort;
  let queryClient: QueryClient;
  let viewModel: SupportingDocumentHistoryViewModel;

  beforeEach(() => {
    deletionPort = new StubExpenseSupportingDocumentDeletionPort();
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    TestBed.configureTestingModule({ providers: [provideTanStackQuery(queryClient)] });
    viewModel = new SupportingDocumentHistoryViewModel(
      deletionPort,
      queryClient,
      TestBed.inject(Injector),
    );
  });

  describe('delete', () => {
    it('deletes the selected document and invalidates only its supporting documents query', async () => {
      queryClient.setQueryData(expenseSupportingDocumentsQueryKey('group-1', 'expense-1'), [
        'cached-document',
      ]);
      queryClient.setQueryData(['groups', 'group-1', 'expenses'], ['cached-expense']);

      await expect(
        viewModel.delete({
          groupId: 'group-1',
          expenseId: 'expense-1',
          sourceUploadIntent: 'intent-1',
        }),
      ).resolves.toBeUndefined();

      expect(deletionPort.commands).toEqual([
        {
          groupId: 'group-1',
          expenseId: 'expense-1',
          sourceUploadIntent: 'intent-1',
        },
      ]);
      await waitFor(() => viewModel.isDeleted());
      expect(
        queryClient.getQueryState(expenseSupportingDocumentsQueryKey('group-1', 'expense-1'))
          ?.isInvalidated,
      ).toBe(true);
      expect(queryClient.getQueryState(['groups', 'group-1', 'expenses'])?.isInvalidated).toBe(
        false,
      );
    });

    it('exposes pending state until deletion is confirmed', async () => {
      deletionPort.defer();

      const deletion = viewModel.delete({
        groupId: 'group-1',
        expenseId: 'expense-1',
        sourceUploadIntent: 'intent-1',
      });

      await waitFor(() => viewModel.isDeleting());
      expect(viewModel.hasDeletionError()).toBe(false);

      deletionPort.resolve();
      await expect(deletion).resolves.toBeUndefined();
      await waitFor(() => viewModel.isDeleted());
    });

    it('exposes a plain error and does not invalidate the query when deletion fails', async () => {
      deletionPort.failure = new Error('Retrait indisponible');
      queryClient.setQueryData(expenseSupportingDocumentsQueryKey('group-1', 'expense-1'), [
        'cached-document',
      ]);

      await expect(
        viewModel.delete({
          groupId: 'group-1',
          expenseId: 'expense-1',
          sourceUploadIntent: 'intent-1',
        }),
      ).rejects.toThrow('Retrait indisponible');

      await waitFor(() => viewModel.hasDeletionError());
      expect(viewModel.deletionErrorMessage()).toBe('Retrait indisponible');
      expect(
        queryClient.getQueryState(expenseSupportingDocumentsQueryKey('group-1', 'expense-1'))
          ?.isInvalidated,
      ).toBe(false);
    });
  });
});

class StubExpenseSupportingDocumentDeletionPort extends ExpenseSupportingDocumentDeletionPort {
  readonly commands: DeleteExpenseSupportingDocumentCommand[] = [];
  failure: Error | null = null;
  private deferred: Promise<void> | null = null;
  private resolveDeferred: (() => void) | null = null;

  override delete(command: DeleteExpenseSupportingDocumentCommand): Promise<void> {
    this.commands.push(command);
    if (this.failure !== null) return Promise.reject(this.failure);
    return this.deferred ?? Promise.resolve();
  }

  defer(): void {
    this.deferred = new Promise((resolve) => {
      this.resolveDeferred = resolve;
    });
  }

  resolve(): void {
    this.resolveDeferred?.();
  }
}

const waitFor = async (condition: () => boolean): Promise<void> => {
  for (let attempt = 0; attempt < 50; attempt += 1) {
    TestBed.tick();
    if (condition()) return;
    await new Promise((resolve) => setTimeout(resolve));
  }

  throw new Error('La condition attendue n a pas ete atteinte.');
};
