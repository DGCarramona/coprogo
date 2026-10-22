import { computed, Injectable, Injector } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { injectQuery } from '@tanstack/angular-query-experimental';

import { ExpenseDetailPort } from '../../application/expense/expense-detail.port';
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
  readonly totalAmount: string;
  readonly createdAt: string;
  readonly statusLabel: string;
  readonly participations: readonly ExpenseParticipationViewItem[];
}

@Injectable()
export class ExpenseDetailPageViewModel {
  readonly groupId: string | null;
  readonly expenseId: string | null;
  private readonly detailQuery;

  readonly detail = computed<ExpenseDetailViewItem | null>(() => this.detailQuery.data() ?? null);
  readonly isLoading = computed(() => this.hasRouteTarget() && this.detailQuery.isLoading());
  readonly hasLoadError = computed(() => !this.hasRouteTarget() || this.detailQuery.isError());
  readonly isReady = computed(() => this.hasRouteTarget() && this.detailQuery.isSuccess());
  readonly errorMessage = computed(() => {
    if (!this.hasRouteTarget()) return 'La dépense demandée est incomplète.';

    const error = this.detailQuery.error();
    return error === null ? null : describeError(error, 'La dépense n’a pas pu être chargée.');
  });

  constructor(
    route: ActivatedRoute,
    private readonly detailPort: ExpenseDetailPort,
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
  }

  retry(): void {
    void this.detailQuery.refetch();
  }

  private hasRouteTarget(): boolean {
    return this.groupId !== null && this.expenseId !== null;
  }
}

const toViewItem = (detail: ExpenseDetail): ExpenseDetailViewItem => ({
  id: detail.id,
  title: detail.title,
  createdBy: detail.createdBy,
  totalAmount: formatMoneyFromCents(detail.totalAmountInCents),
  createdAt: formatDate(detail.createdAt),
  statusLabel: expenseStatusLabel(detail.status),
  participations: detail.participations.map(toParticipationViewItem),
});

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
