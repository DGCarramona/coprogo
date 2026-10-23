import { Injectable } from '@angular/core';
import { catchError, firstValueFrom, map } from 'rxjs';

import { OwnershipShareTimelinePort } from '../../application/revenue/ownership-share-timeline.port';
import type {
  OwnershipShareChange,
  OwnershipShareTimeline,
} from '../../domain/revenue/ownership-share-timeline';
import { parseLocalDate } from '../../shared/format/local-date';
import type {
  OwnershipShareChangeResponseDto,
  OwnershipShareTimelineResponseDto,
} from '../api/generated';
import { RevenueDistributionService } from '../api/generated';
import { toApiClientError } from '../api/api-client.error';

@Injectable({ providedIn: 'root' })
export class HttpOwnershipShareTimelineGateway extends OwnershipShareTimelinePort {
  constructor(private readonly revenueDistributionService: RevenueDistributionService) {
    super();
  }

  override async getByGroup(groupId: string): Promise<OwnershipShareTimeline> {
    return await firstValueFrom(
      this.revenueDistributionService.getOwnershipShareTimeline(groupId).pipe(
        catchError((error) => {
          throw toApiClientError(error, 'L historique des quotes-parts n a pas pu etre charge.');
        }),
        map((response) => toTimeline(response, groupId)),
      ),
    );
  }
}

const toTimeline = (
  response: OwnershipShareTimelineResponseDto,
  expectedGroupId: string,
): OwnershipShareTimeline => {
  if (response.group !== expectedGroupId) {
    throw new Error('Historique des quotes-parts incoherent.');
  }

  return {
    groupId: response.group,
    changes: response.changes.map(toChange),
  };
};

const toChange = (response: OwnershipShareChangeResponseDto): OwnershipShareChange => ({
  id: response.change,
  effectiveDate: requireLocalDate(response.effectiveDate),
  recordedBy: response.recordedBy,
  recordedAt: toDate(response.recordedAt, 'Date d enregistrement des quotes-parts invalide'),
  shares: response.shares.map((share) => ({
    member: share.member,
    percentage: share.percentage,
  })),
});

const requireLocalDate = (value: string): Date => {
  const date = parseLocalDate(value);
  if (date === null) throw invalidEffectiveDate(value);
  return date;
};

const invalidEffectiveDate = (value: string): Error =>
  new Error(`Date d effet des quotes-parts invalide: ${value}.`);

const toDate = (value: string, message: string): Date => {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) throw new Error(`${message}: ${value}.`);
  return date;
};
