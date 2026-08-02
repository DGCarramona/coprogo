import { computed, Injectable, Injector, signal } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';

import { ExpenseListPort } from '../../../application/expense/expense-list.port';
import { describeError } from '../../../application/shared/describe-error';
import { formatMoneyFromCents } from '../../../shared/format/financial-format';
import { ExpenseResponseDto } from '../../../infrastructure/api/generated';

export interface ExpenseViewItem {
  readonly title: string;
  readonly amount: string;
  readonly createdBy: string;
}

@Injectable()
export class ExpenseListWidgetViewModel {
  private readonly groupIdState = signal<string | null>(null);

  private readonly expensesQuery;
  readonly expenses = computed<readonly ExpenseViewItem[]>(() => this.expensesQuery.data() ?? []);
  readonly isLoading = computed(() => this.expensesQuery.isLoading());
  readonly hasLoadError = computed(() => this.expensesQuery.isError());
  readonly isReady = computed(() => this.expensesQuery.isSuccess());
  readonly errorMessage = computed(() => {
    const error = this.expensesQuery.error();

    return error === null ? null : describeError(error, 'Les depenses n ont pas pu etre chargees.');
  });

  constructor(
    private readonly expensePort: ExpenseListPort,
    injector: Injector,
  ) {
    this.expensesQuery = injectQuery(
      () => {
        const groupId = this.groupIdState();

        return {
          queryKey: ['groups', groupId, 'expenses'] as const,
          queryFn: (): Promise<ExpenseResponseDto[]> => {
            if (groupId === null) {
              throw new Error('Un groupe doit etre initialise avant de charger ses depenses.');
            }

            return this.expensePort.listByGroup(groupId);
          },
          enabled: groupId !== null,
          staleTime: 30_000,
          select: (expenses) =>
            expenses.map((expense) => ({
              title: expense.title,
              amount: formatMoneyFromCents(expense.totalAmountCents),
              createdBy: expense.createdBy,
            })),
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
}
