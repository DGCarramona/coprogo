import { computed, Injectable, Injector, signal } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';

import { MemberBalanceImpactHistoryPort } from '../../../application/ledger/member-balance-impact-history.port';
import { describeError } from '../../../application/shared/describe-error';
import type { MemberBalanceImpact } from '../../../domain/ledger/member-balance-impact-history';
import { FinancialFormatter } from '../../i18n/financial-formatter';

interface MemberBalanceImpactTarget {
  readonly groupId: string;
  readonly member: string;
}

export interface MemberBalanceImpactViewItem {
  readonly operationId: string;
  readonly key: string;
  readonly label: string;
  readonly amount: string;
  readonly occurredAt: string;
}

@Injectable()
export class MemberBalanceImpactHistoryWidgetViewModel {
  private readonly targetState = signal<MemberBalanceImpactTarget | null>(null);
  private readonly historyQuery;

  readonly isRequested = computed(() => this.targetState() !== null);
  readonly impacts = computed<readonly MemberBalanceImpactViewItem[]>(
    () => this.historyQuery.data()?.map((impact) => toViewItem(impact, this.formatter)) ?? [],
  );
  readonly isLoading = computed(() => this.historyQuery.isLoading());
  readonly hasLoadError = computed(() => this.historyQuery.isError());
  readonly isReady = computed(() => this.historyQuery.isSuccess());
  readonly errorMessage = computed(() => {
    const error = this.historyQuery.error();

    return error === null ? null : describeError(error, 'errors.balanceImpact.loadHistory');
  });

  constructor(
    private readonly historyPort: MemberBalanceImpactHistoryPort,
    injector: Injector,
    private readonly formatter: FinancialFormatter,
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
            'balance-impacts',
          ] as const,
          queryFn: async (): Promise<readonly MemberBalanceImpact[]> => {
            if (target === null) {
              throw new Error('errors.balanceImpact.missingMember');
            }

            return (await this.historyPort.get(target.groupId, target.member)).impacts;
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

const toViewItem = (
  impact: MemberBalanceImpact,
  formatter: FinancialFormatter,
): MemberBalanceImpactViewItem => ({
  operationId: impact.operationId,
  key: `${impact.type}:${impact.operationId}:${impact.effect}`,
  label: labelFor(impact),
  amount: formatter.money(impact.amountInCents),
  occurredAt: formatter.date(impact.occurredAt),
});

const labelFor = (impact: MemberBalanceImpact): string => {
  switch (impact.type) {
    case 'DIRECT_REIMBURSEMENT':
      return 'balanceImpact.types.reimbursement';
    case 'COMPENSATING_EXPENSE':
      return 'balanceImpact.types.expense';
    case 'CASH_POOL_WITHDRAWAL':
      switch (impact.effect) {
        case 'DEBT_REDUCTION':
          return 'balanceImpact.types.withdrawalBalanceImpact';
        case 'DEBT_INCREASE':
          return 'balanceImpact.types.withdrawalDebtIncrease';
        case 'CREDIT_REDUCTION':
          return 'balanceImpact.types.withdrawalCreditReduction';
      }
  }
};
