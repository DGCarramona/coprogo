import { computed, Injectable, Injector, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';

import { ExpenseDetailPort } from '../../application/expense/expense-detail.port';
import { ExpenseParticipationDecisionPort } from '../../application/expense/expense-participation-decision.port';
import { describeError } from '../../application/shared/describe-error';
import type {
  ExpenseDetail,
  ExpenseParticipation,
  ExpenseParticipationStatus,
} from '../../domain/expense/expense-detail';
import type { ExpenseStatus } from '../../domain/expense/expense-summary';
import { FinancialFormatter } from '../i18n/financial-formatter';

export interface ExpenseParticipationViewItem {
  readonly member: string;
  readonly amount: string;
  readonly statusLabel: string;
  readonly tone: 'pending' | 'approved' | 'refused';
}

export interface ExpenseDetailViewItem {
  readonly id: string;
  readonly title: string;
  readonly createdBy: string;
  readonly canDecideParticipation: boolean;
  readonly totalAmount: string;
  readonly createdAt: string;
  readonly statusLabel: string;
  readonly invalidation: ExpenseInvalidationViewItem | null;
  readonly participations: readonly ExpenseParticipationViewItem[];
}

export interface ExpenseInvalidationViewItem {
  readonly refusedBy: string;
  readonly refusedAt: string;
  readonly reason: string | null;
}

@Injectable()
export class ExpenseDetailPageViewModel {
  readonly groupId: string | null;
  readonly expenseId: string | null;
  private readonly detailQuery;
  private readonly approvalMutation;
  private readonly refusalMutation;
  private readonly decisionErrorState = signal<string | null>(null);
  readonly rejectionReason = signal('');

  readonly detail = computed<ExpenseDetailViewItem | null>(() => {
    const detail = this.detailQuery.data();
    return detail === undefined ? null : toViewItem(detail, this.formatter);
  });
  readonly isLoading = computed(() => this.hasRouteTarget() && this.detailQuery.isLoading());
  readonly hasLoadError = computed(() => !this.hasRouteTarget() || this.detailQuery.isError());
  readonly isReady = computed(() => this.hasRouteTarget() && this.detailQuery.isSuccess());
  readonly errorMessage = computed(() => {
    if (!this.hasRouteTarget()) return 'errors.expenses.incompleteTarget';

    const error = this.detailQuery.error();
    return error === null ? null : describeError(error, 'errors.expenses.loadDetail');
  });
  readonly canDecide = computed(() => this.detail()?.canDecideParticipation ?? false);
  readonly isDeciding = computed(
    () => this.approvalMutation.isPending() || this.refusalMutation.isPending(),
  );
  readonly decisionError = this.decisionErrorState.asReadonly();

  constructor(
    route: ActivatedRoute,
    private readonly detailPort: ExpenseDetailPort,
    private readonly decisionPort: ExpenseParticipationDecisionPort,
    private readonly queryClient: QueryClient,
    injector: Injector,
    private readonly formatter: FinancialFormatter,
  ) {
    this.groupId = route.snapshot.paramMap.get('groupId');
    this.expenseId = route.snapshot.paramMap.get('expenseId');
    this.detailQuery = injectQuery(
      () => ({
        queryKey: ['groups', this.groupId, 'expenses', this.expenseId] as const,
        queryFn: async (): Promise<ExpenseDetail> => {
          if (this.groupId === null || this.expenseId === null) {
            throw new Error('errors.expenses.missingDetailTarget');
          }

          return this.detailPort.get(this.groupId, this.expenseId);
        },
        enabled: this.hasRouteTarget(),
        staleTime: 30_000,
        select: (detail) => {
          requireCompleteRefusal(detail);
          return detail;
        },
      }),
      { injector },
    );
    this.approvalMutation = injectMutation<void, Error, ExpenseDecisionTarget>(
      () => ({
        mutationFn: ({ groupId, expenseId }) => this.decisionPort.approve(groupId, expenseId),
        onSuccess: (_, target) => this.refreshExpenseQueries(target),
      }),
      { injector },
    );
    this.refusalMutation = injectMutation<void, Error, ExpenseRefusalTarget>(
      () => ({
        mutationFn: ({ groupId, expenseId, reason }) =>
          this.decisionPort.refuse(groupId, expenseId, reason),
        onSuccess: (_, target) => this.refreshExpenseQueries(target),
      }),
      { injector },
    );
  }

  retry(): void {
    void this.detailQuery.refetch();
  }

  async approve(): Promise<void> {
    await this.decide((target) => this.approvalMutation.mutateAsync(target));
  }

  async refuse(): Promise<void> {
    const enteredReason = this.rejectionReason().trim();
    await this.decide((target) =>
      this.refusalMutation.mutateAsync({
        ...target,
        reason: enteredReason.length === 0 ? null : enteredReason,
      }),
    );
  }

  private hasRouteTarget(): boolean {
    return this.groupId !== null && this.expenseId !== null;
  }

  private async decide(action: (target: ExpenseDecisionTarget) => Promise<void>): Promise<void> {
    this.decisionErrorState.set(null);
    const target = this.decisionTarget();
    if (target === null) {
      this.decisionErrorState.set('errors.expenses.noPendingParticipation');
      return;
    }

    try {
      await action(target);
    } catch (error) {
      this.decisionErrorState.set(describeError(error, 'errors.expenses.recordDecision'));
    }
  }

  private decisionTarget(): ExpenseDecisionTarget | null {
    if (!this.canDecide() || this.groupId === null || this.expenseId === null) return null;
    return { groupId: this.groupId, expenseId: this.expenseId };
  }

  private async refreshExpenseQueries(target: ExpenseDecisionTarget): Promise<void> {
    await Promise.all([
      this.queryClient.invalidateQueries({
        queryKey: ['groups', target.groupId, 'expenses', target.expenseId],
        exact: true,
      }),
      this.queryClient.invalidateQueries({
        queryKey: ['groups', target.groupId, 'expenses'],
        exact: true,
      }),
    ]);
  }
}

interface ExpenseDecisionTarget {
  readonly groupId: string;
  readonly expenseId: string;
}

interface ExpenseRefusalTarget extends ExpenseDecisionTarget {
  readonly reason: string | null;
}

const toViewItem = (
  detail: ExpenseDetail,
  formatter: FinancialFormatter,
): ExpenseDetailViewItem => ({
  id: detail.id,
  title: detail.title,
  createdBy: detail.createdBy,
  canDecideParticipation: detail.canDecideParticipation,
  totalAmount: formatter.money(detail.totalAmountInCents),
  createdAt: formatter.date(detail.createdAt),
  statusLabel: expenseStatusLabel(detail.status),
  invalidation: toInvalidationViewItem(detail, formatter),
  participations: detail.participations.map((participation) =>
    toParticipationViewItem(participation, formatter),
  ),
});

const toInvalidationViewItem = (
  detail: ExpenseDetail,
  formatter: FinancialFormatter,
): ExpenseInvalidationViewItem | null => {
  if (detail.status !== 'INVALIDATED') return null;
  const refusal = requireCompleteRefusal(detail);
  if (refusal === null) return null;

  return {
    refusedBy: refusal.member,
    refusedAt: formatter.date(refusal.refusedAt),
    reason: refusal.reason,
  };
};

const toParticipationViewItem = (
  participation: ExpenseParticipation,
  formatter: FinancialFormatter,
): ExpenseParticipationViewItem => ({
  member: participation.member,
  amount: formatter.money(participation.amountInCents),
  statusLabel: participationStatusLabel(participation.status),
  tone: participationTone(participation.status),
});

const expenseStatusLabel = (status: ExpenseStatus): string => {
  switch (status) {
    case 'PROPOSED':
      return 'statuses.pendingApproval';
    case 'ACCEPTED':
      return 'statuses.approved';
    case 'INVALIDATED':
      return 'statuses.needsReplacement';
  }
};

const participationStatusLabel = (status: ExpenseParticipationStatus): string => {
  switch (status) {
    case 'PENDING':
      return 'statuses.pendingValidation';
    case 'APPROVED':
      return 'statuses.approved';
    case 'REFUSED':
      return 'statuses.refused';
  }
};

const participationTone = (
  status: ExpenseParticipationStatus,
): ExpenseParticipationViewItem['tone'] => {
  switch (status) {
    case 'PENDING':
      return 'pending';
    case 'APPROVED':
      return 'approved';
    case 'REFUSED':
      return 'refused';
  }
};

const requireCompleteRefusal = (detail: ExpenseDetail) => {
  if (detail.status === 'INVALIDATED' && detail.refusal === null) {
    throw new Error('errors.expenses.missingRefusal');
  }
  return detail.refusal;
};
