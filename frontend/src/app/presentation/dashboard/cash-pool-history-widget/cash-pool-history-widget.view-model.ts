import { computed, Injectable, Injector, signal } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';

import { CashPoolHistoryPort } from '../../../application/ledger/cash-pool-history.port';
import { describeError } from '../../../application/shared/describe-error';
import type { CashPoolOperation } from '../../../domain/ledger/cash-pool-history';
import { FinancialFormatter } from '../../i18n/financial-formatter';
import { cashPoolHistoryQueryKey } from '../cash-pool-query-keys';

interface CashPoolOperationViewItemBase {
  readonly operationId: string;
  readonly occurredAt: string;
  readonly label: string;
  readonly amount: string;
}

export interface CashPoolIncomeViewItem extends CashPoolOperationViewItemBase {
  readonly type: 'INCOME';
  readonly allocations: readonly { member: string; amount: string }[];
}

export interface CashPoolWithdrawalViewItem extends CashPoolOperationViewItemBase {
  readonly type: 'WITHDRAWAL';
  readonly withdrawnBy: string;
  readonly ownRevenueShareConsumed: string;
  readonly debtReduction: string;
  readonly hasDebtReduction: boolean;
}

export type CashPoolOperationViewItem = CashPoolIncomeViewItem | CashPoolWithdrawalViewItem;

@Injectable()
export class CashPoolHistoryWidgetViewModel {
  private readonly groupIdState = signal<string | null>(null);
  private readonly historyQuery;

  readonly operations = computed<readonly CashPoolOperationViewItem[]>(
    () => this.historyQuery.data()?.map((operation) => toViewItem(operation, this.formatter)) ?? [],
  );
  readonly isLoading = computed(() => this.historyQuery.isLoading());
  readonly hasLoadError = computed(() => this.historyQuery.isError());
  readonly isReady = computed(() => this.historyQuery.isSuccess());
  readonly errorMessage = computed(() => {
    const error = this.historyQuery.error();
    return error === null ? null : describeError(error, 'errors.cashPool.loadHistory');
  });

  constructor(
    historyPort: CashPoolHistoryPort,
    injector: Injector,
    private readonly formatter: FinancialFormatter,
  ) {
    this.historyQuery = injectQuery(
      () => {
        const groupId = this.groupIdState();
        return {
          queryKey: cashPoolHistoryQueryKey(groupId),
          queryFn: async (): Promise<readonly CashPoolOperation[]> => {
            if (groupId === null) {
              throw new Error('errors.cashPool.missingHistoryGroup');
            }
            return (await historyPort.get(groupId)).operations;
          },
          enabled: groupId !== null,
          staleTime: 30_000,
        };
      },
      { injector },
    );
  }

  load(groupId: string): void {
    this.groupIdState.set(groupId);
  }

  retry(): void {
    void this.historyQuery.refetch();
  }
}

const toViewItem = (
  operation: CashPoolOperation,
  formatter: FinancialFormatter,
): CashPoolOperationViewItem => {
  const common = {
    operationId: operation.operationId,
    occurredAt: formatter.date(operation.occurredAt),
    amount: formatter.money(operation.amountInCents),
  };

  switch (operation.type) {
    case 'INCOME':
      return {
        ...common,
        type: operation.type,
        label: 'cashPool.history.incomeAdded',
        allocations: operation.allocations.map((allocation) => ({
          member: allocation.member,
          amount: formatter.money(allocation.amountInCents),
        })),
      };
    case 'WITHDRAWAL':
      return {
        ...common,
        type: operation.type,
        label: 'cashPool.history.withdrawalBy',
        withdrawnBy: operation.withdrawnBy,
        ownRevenueShareConsumed: formatter.money(operation.ownRevenueShareConsumedInCents),
        debtReduction: formatter.money(
          operation.amountInCents - operation.ownRevenueShareConsumedInCents,
        ),
        hasDebtReduction: operation.amountInCents > operation.ownRevenueShareConsumedInCents,
      };
  }
};
