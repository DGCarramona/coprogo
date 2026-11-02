import { computed, Injectable, Injector, signal } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';

import { ExpenseListPort } from '../../../application/expense/expense-list.port';
import { describeError } from '../../../application/shared/describe-error';
import type { ExpenseSummary } from '../../../domain/expense/expense-summary';
import { FinancialFormatter } from '../../i18n/financial-formatter';

export interface ExpenseViewItem {
  readonly id: string;
  readonly title: string;
  readonly amount: string;
  readonly createdBy: string;
}

@Injectable()
export class ExpenseListWidgetViewModel {
  private readonly groupIdState = signal<string | null>(null);
  private readonly openedSupportingDocumentExpenseIds = signal<ReadonlySet<string>>(new Set());

  private readonly expensesQuery;
  readonly expenses = computed<readonly ExpenseViewItem[]>(
    () =>
      this.expensesQuery.data()?.map((expense) => ({
        id: expense.id,
        title: expense.title,
        amount: this.formatter.money(expense.totalAmountInCents),
        createdBy: expense.createdBy,
      })) ?? [],
  );
  readonly isLoading = computed(() => this.expensesQuery.isLoading());
  readonly hasLoadError = computed(() => this.expensesQuery.isError());
  readonly isReady = computed(() => this.expensesQuery.isSuccess());
  readonly errorMessage = computed(() => {
    const error = this.expensesQuery.error();

    return error === null ? null : describeError(error, 'errors.expenses.loadList');
  });

  constructor(
    private readonly expensePort: ExpenseListPort,
    injector: Injector,
    private readonly formatter: FinancialFormatter,
  ) {
    this.expensesQuery = injectQuery(
      () => {
        const groupId = this.groupIdState();

        return {
          queryKey: ['groups', groupId, 'expenses'] as const,
          queryFn: (): Promise<readonly ExpenseSummary[]> => {
            if (groupId === null) {
              throw new Error('errors.expenses.missingListGroup');
            }

            return this.expensePort.listByGroup(groupId);
          },
          enabled: groupId !== null,
          staleTime: 30_000,
        };
      },
      { injector },
    );
  }

  initialize(groupId: string): void {
    this.groupIdState.set(groupId);
  }

  retry(): void {
    void this.expensesQuery.refetch();
  }

  setSupportingDocumentsOpen(expenseId: string, isOpen: boolean): void {
    this.openedSupportingDocumentExpenseIds.update((openedExpenseIds) => {
      const nextOpenedExpenseIds = new Set(openedExpenseIds);
      if (isOpen) {
        nextOpenedExpenseIds.add(expenseId);
      } else {
        nextOpenedExpenseIds.delete(expenseId);
      }

      return nextOpenedExpenseIds;
    });
  }

  isSupportingDocumentsOpen(expenseId: string): boolean {
    return this.openedSupportingDocumentExpenseIds().has(expenseId);
  }
}
