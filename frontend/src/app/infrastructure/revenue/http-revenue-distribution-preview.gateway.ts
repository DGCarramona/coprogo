import { Injectable } from '@angular/core';
import { catchError, firstValueFrom, map } from 'rxjs';

import {
  RevenueDistributionPreviewPort,
  type RevenueDistributionPreviewQuery,
} from '../../application/revenue/revenue-distribution-preview.port';
import type { RevenueDistributionPreview } from '../../domain/revenue/revenue-distribution-preview';
import { formatLocalDate, parseLocalDate } from '../../shared/format/local-date';
import { toApiClientError } from '../api/api-client.error';
import {
  RevenueDistributionService,
  type RevenueDistributionAtDatePreviewResponseDto,
} from '../api/generated';

@Injectable({ providedIn: 'root' })
export class HttpRevenueDistributionPreviewGateway extends RevenueDistributionPreviewPort {
  constructor(private readonly revenueDistributionService: RevenueDistributionService) {
    super();
  }

  override async preview(
    query: RevenueDistributionPreviewQuery,
  ): Promise<RevenueDistributionPreview> {
    return await firstValueFrom(
      this.revenueDistributionService
        .previewAtDate(query.groupId, {
          amountInCents: query.amountInCents,
          effectiveDate: formatLocalDate(query.effectiveDate),
        })
        .pipe(
          catchError((error) => {
            throw toApiClientError(error, 'La repartition des revenus n a pas pu etre calculee.');
          }),
          map((response) => toPreview(response, query.groupId)),
        ),
    );
  }
}

const toPreview = (
  response: RevenueDistributionAtDatePreviewResponseDto,
  expectedGroupId: string,
): RevenueDistributionPreview => {
  if (response.group !== expectedGroupId) {
    throw new Error('Prévisualisation incohérente avec le groupe demandé.');
  }

  const effectiveDate = parseLocalDate(response.effectiveDate);
  if (effectiveDate === null) {
    throw new Error(`Date de prévisualisation invalide: ${response.effectiveDate}.`);
  }

  return {
    groupId: response.group,
    effectiveDate,
    totalAmountInCents: response.totalAmountInCents,
    allocations: response.allocations.map((allocation) => ({
      member: allocation.memberId,
      amountInCents: allocation.amountInCents,
    })),
  };
};
