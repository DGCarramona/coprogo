import { Injectable } from '@angular/core';
import { catchError, firstValueFrom, map } from 'rxjs';

import {
  CashPoolIncomeRecordingPort,
  type CashPoolIncomeRecordingCommand,
} from '../../application/revenue/cash-pool-income-recording.port';
import { formatLocalDate } from '../../shared/format/local-date';
import { toApiClientError } from '../api/api-client.error';
import { RevenueDistributionService } from '../api/generated';

@Injectable({ providedIn: 'root' })
export class HttpCashPoolIncomeRecordingGateway extends CashPoolIncomeRecordingPort {
  constructor(private readonly revenueDistributionService: RevenueDistributionService) {
    super();
  }

  override async record(command: CashPoolIncomeRecordingCommand): Promise<void> {
    await firstValueFrom(
      this.revenueDistributionService
        .recordCashPoolIncome(command.groupId, {
          amountInCents: command.amountInCents,
          receivedAt: command.receivedAt.toISOString(),
          effectiveDate: formatLocalDate(command.effectiveDate),
        })
        .pipe(
          map(() => undefined),
          catchError((error) => {
            throw toApiClientError(error, 'Le revenu n a pas pu etre enregistre.');
          }),
        ),
    );
  }
}
