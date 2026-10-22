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
import { formatDate, formatMoneyFromCents } from '../../shared/format/financial-format';

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

  readonly detail = computed<ExpenseDetailViewItem | null>(() => this.detailQuery.data() ?? null);
  readonly isLoading = computed(() => this.hasRouteTarget() && this.detailQuery.isLoading());
  readonly hasLoadError = computed(() => !this.hasRouteTarget() || this.detailQuery.isError());
  readonly isReady = computed(() => this.hasRouteTarget() && this.detailQuery.isSuccess());
  readonly errorMessage = computed(() => {
    if (!this.hasRouteTarget()) return 'La dépense demandée est incomplète.';

    const error = this.detailQuery.error();
    return error === null ? null : describeError(error, 'La dépense n’a pas pu être chargée.');
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
  ) {
    this.groupId = route.snapshot.paramMap.get('groupId');
    this.expenseId = route.snapshot.paramMap.get('expenseId');
    this.detailQuery = injectQuery(
      () => ({
        queryKey: ['groups', this.groupId, 'expenses', this.expenseId] as const,
        queryFn: async (): Promise<ExpenseDetail> => {
          if (this.groupId === null || this.expenseId === null) {
            throw new Error('Une dépense complète doit être sélectionnée avant son chargement.');
          }

          return this.detailPort.get(this.groupId, this.expenseId);
        },
        enabled: this.hasRouteTarget(),
        staleTime: 30_000,
        select: toViewItem,
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
      this.decisionErrorState.set('Aucune participation en attente ne peut être validée.');
      return;
    }

    try {
      await action(target);
    } catch (error) {
      this.decisionErrorState.set(
        describeError(error, 'La décision sur la participation n’a pas pu être enregistrée.'),
      );
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

const toViewItem = (detail: ExpenseDetail): ExpenseDetailViewItem => ({
  id: detail.id,
  title: detail.title,
  createdBy: detail.createdBy,
  canDecideParticipation: detail.canDecideParticipation,
  totalAmount: formatMoneyFromCents(detail.totalAmountInCents),
  createdAt: formatDate(detail.createdAt),
  statusLabel: expenseStatusLabel(detail.status),
  invalidation: toInvalidationViewItem(detail),
  participations: detail.participations.map(toParticipationViewItem),
});

const toInvalidationViewItem = (detail: ExpenseDetail): ExpenseInvalidationViewItem | null => {
  if (detail.status !== 'INVALIDATED') return null;
  if (detail.refusal === null) {
    throw new Error('Une dépense invalidée doit contenir son refus.');
  }

  return {
    refusedBy: detail.refusal.member,
    refusedAt: formatDate(detail.refusal.refusedAt),
    reason: detail.refusal.reason,
  };
};

const toParticipationViewItem = (
  participation: ExpenseParticipation,
): ExpenseParticipationViewItem => ({
  member: participation.member,
  amount: formatMoneyFromCents(participation.amountInCents),
  statusLabel: participationStatusLabel(participation.status),
  tone: participationTone(participation.status),
});

const expenseStatusLabel = (status: ExpenseStatus): string => {
  switch (status) {
    case 'PROPOSED':
      return 'En attente de validation';
    case 'ACCEPTED':
      return 'Validée';
    case 'INVALIDATED':
      return 'À reprendre';
  }
};

const participationStatusLabel = (status: ExpenseParticipationStatus): string => {
  switch (status) {
    case 'PENDING':
      return 'À valider';
    case 'APPROVED':
      return 'Validée';
    case 'REFUSED':
      return 'Refusée';
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
