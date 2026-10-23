import { computed, Injectable, Injector } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { injectQuery } from '@tanstack/angular-query-experimental';

import { OwnershipShareTimelinePort } from '../../application/revenue/ownership-share-timeline.port';
import { describeError } from '../../application/shared/describe-error';
import type { OwnershipShareChange } from '../../domain/revenue/ownership-share-timeline';
import { formatDate, formatPercentage } from '../../shared/format/financial-format';

export interface OwnershipShareChangeViewItem {
  readonly id: string;
  readonly effectiveDate: string;
  readonly recordedBy: string;
  readonly recordedAt: string;
  readonly shares: readonly OwnershipShareViewItem[];
}

export interface OwnershipShareViewItem {
  readonly member: string;
  readonly percentage: string;
}

@Injectable()
export class OwnershipShareTimelinePageViewModel {
  readonly groupId: string | null;
  private readonly timelineQuery;

  readonly changes = computed<readonly OwnershipShareChangeViewItem[]>(
    () => this.timelineQuery.data() ?? [],
  );
  readonly isLoading = computed(() => this.groupId !== null && this.timelineQuery.isLoading());
  readonly hasLoadError = computed(() => this.groupId === null || this.timelineQuery.isError());
  readonly isReady = computed(() => this.groupId !== null && this.timelineQuery.isSuccess());
  readonly errorMessage = computed(() => {
    if (this.groupId === null) return 'Aucun groupe n’est sélectionné.';

    const error = this.timelineQuery.error();
    return error === null
      ? null
      : describeError(error, 'L’historique des quotes-parts n’a pas pu être chargé.');
  });

  constructor(
    route: ActivatedRoute,
    private readonly timelinePort: OwnershipShareTimelinePort,
    injector: Injector,
  ) {
    this.groupId = route.snapshot.paramMap.get('groupId');
    this.timelineQuery = injectQuery(
      () => ({
        queryKey: ['groups', this.groupId, 'ownership-shares'] as const,
        queryFn: async (): Promise<readonly OwnershipShareChange[]> => {
          if (this.groupId === null) {
            throw new Error('Un groupe doit être sélectionné avant de charger les quotes-parts.');
          }

          return (await this.timelinePort.getByGroup(this.groupId)).changes;
        },
        enabled: this.groupId !== null,
        staleTime: 30_000,
        select: (changes) => changes.map(toViewItem),
      }),
      { injector },
    );
  }

  retry(): void {
    void this.timelineQuery.refetch();
  }
}

const toViewItem = (change: OwnershipShareChange): OwnershipShareChangeViewItem => ({
  id: change.id,
  effectiveDate: formatDate(change.effectiveDate),
  recordedBy: change.recordedBy,
  recordedAt: formatDate(change.recordedAt),
  shares: change.shares.map((share) => ({
    member: share.member,
    percentage: formatPercentage(share.percentage),
  })),
});
