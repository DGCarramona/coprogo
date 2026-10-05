import { computed, Injectable, Injector } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { injectQuery } from '@tanstack/angular-query-experimental';

import { ReimbursementListPort } from '../../application/reimbursement/reimbursement-list.port';
import { describeError } from '../../application/shared/describe-error';
import type { ReimbursementSummary } from '../../domain/reimbursement/reimbursement-summary';
import { formatDate, formatMoneyFromCents } from '../../shared/format/financial-format';

export interface ReimbursementHistoryViewItem {
  readonly id: string;
  readonly paidBy: string;
  readonly receivedBy: string;
  readonly amount: string;
  readonly reimbursedAt: string;
  readonly awaitsReview: boolean;
  readonly documents: readonly ReimbursementSupportingDocumentViewItem[];
}

export interface ReimbursementSupportingDocumentViewItem {
  readonly sourceUploadIntent: string;
  readonly fileName: string;
  readonly uploader: string;
  readonly attachedAt: string;
  readonly downloadUrl: string;
}

@Injectable()
export class ReimbursementHistoryPageViewModel {
  readonly groupId: string | null;
  private readonly reimbursementsQuery;

  readonly reimbursements = computed<readonly ReimbursementHistoryViewItem[]>(
    () => this.reimbursementsQuery.data() ?? [],
  );
  readonly isLoading = computed(
    () => this.groupId !== null && this.reimbursementsQuery.isLoading(),
  );
  readonly hasLoadError = computed(
    () => this.groupId === null || this.reimbursementsQuery.isError(),
  );
  readonly isReady = computed(() => this.groupId !== null && this.reimbursementsQuery.isSuccess());
  readonly errorMessage = computed(() => {
    if (this.groupId === null) return 'Aucun groupe n est selectionne.';

    const error = this.reimbursementsQuery.error();
    return error === null
      ? null
      : describeError(error, 'Les remboursements n ont pas pu etre charges.');
  });

  constructor(
    route: ActivatedRoute,
    private readonly reimbursementPort: ReimbursementListPort,
    injector: Injector,
  ) {
    this.groupId = route.snapshot.paramMap.get('groupId');
    this.reimbursementsQuery = injectQuery(
      () => ({
        queryKey: ['groups', this.groupId, 'reimbursements'] as const,
        queryFn: (): Promise<readonly ReimbursementSummary[]> => {
          if (this.groupId === null) {
            throw new Error('Un groupe doit etre selectionne avant de charger ses remboursements.');
          }

          return this.reimbursementPort.listByGroup(this.groupId);
        },
        enabled: this.groupId !== null,
        staleTime: 30_000,
        select: (reimbursements) => reimbursements.map(toViewItem),
      }),
      { injector },
    );
  }

  retry(): void {
    void this.reimbursementsQuery.refetch();
  }
}

const toViewItem = (reimbursement: ReimbursementSummary): ReimbursementHistoryViewItem => ({
  id: reimbursement.id,
  paidBy: reimbursement.paidBy,
  receivedBy: reimbursement.receivedBy,
  amount: formatMoneyFromCents(reimbursement.amountInCents),
  reimbursedAt: formatDate(reimbursement.reimbursedAt),
  awaitsReview: reimbursement.status === 'PENDING_REVIEW',
  documents: reimbursement.documents.map((document) => ({
    sourceUploadIntent: document.sourceUploadIntent,
    fileName: document.fileName,
    uploader: document.uploader,
    attachedAt: formatDate(document.attachedAt),
    downloadUrl: document.downloadTarget.url,
  })),
});
