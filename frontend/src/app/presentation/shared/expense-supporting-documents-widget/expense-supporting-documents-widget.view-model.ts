import { computed, Injectable, Injector, signal } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';

import {
  ExpenseSupportingDocumentsPort,
  type ExpenseSupportingDocuments,
} from '../../../application/supporting-document/expense-supporting-documents.port';
import { describeError } from '../../../application/shared/describe-error';
import { reconstructSupportingDocumentHistoryChains } from '../supporting-document/expense-supporting-document-history-chains';
import { expenseSupportingDocumentsQueryKey } from '../supporting-document/expense-supporting-documents-query-key';

@Injectable()
export class ExpenseSupportingDocumentsWidgetViewModel {
  private readonly groupIdState = signal<string | null>(null);
  private readonly expenseIdState = signal<string | null>(null);
  private readonly documentsQuery;

  readonly histories = computed(() =>
    reconstructSupportingDocumentHistoryChains(this.documentsQuery.data()?.history ?? []),
  );
  readonly isLoading = computed(() => this.documentsQuery.isLoading());
  readonly hasLoadError = computed(() => this.documentsQuery.isError());
  readonly isReady = computed(() => this.documentsQuery.isSuccess());
  readonly errorMessage = computed(() => {
    const error = this.documentsQuery.error();
    return error === null
      ? null
      : describeError(error, 'Les justificatifs n ont pas pu etre charges.');
  });

  constructor(
    private readonly supportingDocumentsPort: ExpenseSupportingDocumentsPort,
    injector: Injector,
  ) {
    this.documentsQuery = injectQuery(
      () => {
        const groupId = this.groupIdState();
        const expenseId = this.expenseIdState();

        return {
          queryKey: expenseSupportingDocumentsQueryKey(groupId ?? '', expenseId ?? ''),
          queryFn: (): Promise<ExpenseSupportingDocuments> => {
            if (groupId === null || expenseId === null) {
              throw new Error(
                'Une depense doit etre initialisee avant de charger ses justificatifs.',
              );
            }

            return this.supportingDocumentsPort.listByExpense(groupId, expenseId);
          },
          enabled: groupId !== null && expenseId !== null,
          staleTime: 30_000,
        };
      },
      { injector },
    );
  }

  initialize(groupId: string, expenseId: string): void {
    this.groupIdState.set(groupId);
    this.expenseIdState.set(expenseId);
  }

  retry(): void {
    void this.documentsQuery.refetch();
  }
}
