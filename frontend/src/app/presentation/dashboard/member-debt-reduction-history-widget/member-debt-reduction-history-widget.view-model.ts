import { computed, Injectable, Injector, signal } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';

import { MemberDebtReductionHistoryPort } from '../../../application/ledger/member-debt-reduction-history.port';
import { describeError } from '../../../application/shared/describe-error';
import type {
  MemberDebtReduction,
  MemberDebtReductionType,
} from '../../../domain/ledger/member-debt-reduction-history';
import { formatDate, formatMoneyFromCents } from '../../../shared/format/financial-format';

interface MemberDebtReductionTarget {
  readonly groupId: string;
  readonly member: string;
}

export interface MemberDebtReductionViewItem {
  readonly operationId: string;
  readonly label: string;
  readonly amount: string;
  readonly occurredAt: string;
}

@Injectable()
export class MemberDebtReductionHistoryWidgetViewModel {
  private readonly targetState = signal<MemberDebtReductionTarget | null>(null);
  private readonly historyQuery;

  readonly isRequested = computed(() => this.targetState() !== null);
  readonly reductions = computed<readonly MemberDebtReductionViewItem[]>(
    () => this.historyQuery.data() ?? [],
  );
  readonly isLoading = computed(() => this.historyQuery.isLoading());
  readonly hasLoadError = computed(() => this.historyQuery.isError());
  readonly isReady = computed(() => this.historyQuery.isSuccess());
  readonly errorMessage = computed(() => {
    const error = this.historyQuery.error();

    return error === null
      ? null
      : describeError(
          error,
          'Les operations qui ont reduit cette dette n ont pas pu etre chargees.',
        );
  });

  constructor(
    private readonly historyPort: MemberDebtReductionHistoryPort,
    injector: Injector,
  ) {
    this.historyQuery = injectQuery(
      () => {
        const target = this.targetState();

        return {
          queryKey: [
            'groups',
            target?.groupId,
            'members',
            target?.member,
            'debt-reductions',
          ] as const,
          queryFn: async (): Promise<readonly MemberDebtReductionViewItem[]> => {
            if (target === null) {
              throw new Error('Un membre doit etre choisi avant de charger son historique.');
            }

            return (await this.historyPort.get(target.groupId, target.member)).reductions.map(
              toViewItem,
            );
          },
          enabled: target !== null,
          staleTime: 30_000,
        };
      },
      { injector },
    );
  }

  load(groupId: string, member: string): void {
    this.targetState.set({ groupId, member });
  }

  retry(): void {
    void this.historyQuery.refetch();
  }
}

const toViewItem = (reduction: MemberDebtReduction): MemberDebtReductionViewItem => ({
  operationId: reduction.operationId,
  label: labelFor(reduction.type),
  amount: formatMoneyFromCents(reduction.amountInCents),
  occurredAt: formatDate(reduction.occurredAt),
});

const labelFor = (type: MemberDebtReductionType): string => {
  switch (type) {
    case 'DIRECT_REIMBURSEMENT':
      return 'Remboursement effectué';
    case 'COMPENSATING_EXPENSE':
      return 'Dépense ayant réduit la dette';
    case 'CASH_POOL_WITHDRAWAL':
      return 'Retrait de caisse commune';
  }
};
