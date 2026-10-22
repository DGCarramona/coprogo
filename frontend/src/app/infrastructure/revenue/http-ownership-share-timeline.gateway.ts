import { Injectable } from '@angular/core';
import { catchError, firstValueFrom, map } from 'rxjs';

import { OwnershipShareTimelinePort } from '../../application/revenue/ownership-share-timeline.port';
import type {
  OwnershipShareChange,
  OwnershipShareTimeline,
} from '../../domain/revenue/ownership-share-timeline';
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
  effectiveDate: toLocalDate(response.effectiveDate),
  recordedBy: response.recordedBy,
  recordedAt: toDate(response.recordedAt, 'Date d enregistrement des quotes-parts invalide'),
  shares: response.shares.map((share) => ({
    member: share.member,
    percentage: share.percentage,
  })),
});

const toLocalDate = (value: string): Date => {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (match === null) throw invalidEffectiveDate(value);

  const [, yearText, monthText, dayText] = match;
  const year = Number(yearText);
  const month = Number(monthText);
  const day = Number(dayText);
  const date = new Date(year, month - 1, day);
  if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) {
    throw invalidEffectiveDate(value);
  }

  return date;
};

const invalidEffectiveDate = (value: string): Error =>
  new Error(`Date d effet des quotes-parts invalide: ${value}.`);

const toDate = (value: string, message: string): Date => {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) throw new Error(`${message}: ${value}.`);
  return date;
};
