import { computed, Injectable, Injector, signal } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';

import { CashPoolHistoryPort } from '../../../application/ledger/cash-pool-history.port';
import { describeError } from '../../../application/shared/describe-error';
import type { CashPoolOperation } from '../../../domain/ledger/cash-pool-history';
import { formatDate, formatMoneyFromCents } from '../../../shared/format/financial-format';
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
    () => this.historyQuery.data() ?? [],
  );
  readonly isLoading = computed(() => this.historyQuery.isLoading());
  readonly hasLoadError = computed(() => this.historyQuery.isError());
  readonly isReady = computed(() => this.historyQuery.isSuccess());
  readonly errorMessage = computed(() => {
    const error = this.historyQuery.error();
    return error === null
      ? null
      : describeError(error, 'L’historique de la caisse commune n’a pas pu être chargé.');
  });

  constructor(historyPort: CashPoolHistoryPort, injector: Injector) {
    this.historyQuery = injectQuery(
      () => {
        const groupId = this.groupIdState();
        return {
          queryKey: cashPoolHistoryQueryKey(groupId),
          queryFn: async (): Promise<readonly CashPoolOperationViewItem[]> => {
            if (groupId === null) {
              throw new Error('Un groupe doit être choisi avant de charger son historique.');
            }
            return (await historyPort.get(groupId)).operations.map(toViewItem);
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

const toViewItem = (operation: CashPoolOperation): CashPoolOperationViewItem => {
  const common = {
    operationId: operation.operationId,
    occurredAt: formatDate(operation.occurredAt),
    amount: formatMoneyFromCents(operation.amountInCents),
  };

  switch (operation.type) {
    case 'INCOME':
      return {
        ...common,
        type: operation.type,
        label: 'Revenu ajouté',
        allocations: operation.allocations.map((allocation) => ({
          member: allocation.member,
          amount: formatMoneyFromCents(allocation.amountInCents),
        })),
      };
    case 'WITHDRAWAL':
      return {
        ...common,
        type: operation.type,
        label: `Retrait par ${operation.withdrawnBy}`,
        ownRevenueShareConsumed: formatMoneyFromCents(operation.ownRevenueShareConsumedInCents),
        debtReduction: formatMoneyFromCents(
          operation.amountInCents - operation.ownRevenueShareConsumedInCents,
        ),
        hasDebtReduction: operation.amountInCents > operation.ownRevenueShareConsumedInCents,
      };
  }
};
