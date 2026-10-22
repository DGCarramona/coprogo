import { Injectable } from '@angular/core';
import { catchError, firstValueFrom, map } from 'rxjs';

import {
  OwnershipShareChangePort,
  type OwnershipShareChangeCommand,
} from '../../application/revenue/ownership-share-change.port';
import { formatLocalDate } from '../../shared/format/local-date';
import { RevenueDistributionService } from '../api/generated';
import { toApiClientError } from '../api/api-client.error';

@Injectable({ providedIn: 'root' })
export class HttpOwnershipShareChangeGateway extends OwnershipShareChangePort {
  constructor(private readonly revenueDistributionService: RevenueDistributionService) {
    super();
  }

  override async record(command: OwnershipShareChangeCommand): Promise<void> {
    await firstValueFrom(
      this.revenueDistributionService
        .recordOwnershipShareChange(command.groupId, {
          effectiveDate: formatLocalDate(command.effectiveDate),
          shares: [...command.shares],
        })
        .pipe(
          map(() => undefined),
          catchError((error) => {
            throw toApiClientError(error, 'Les quotes-parts n ont pas pu etre enregistrees.');
          }),
        ),
    );
  }
}
