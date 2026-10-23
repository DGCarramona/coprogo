import { Injectable } from '@angular/core';
import { catchError, firstValueFrom, map } from 'rxjs';

import {
  CashPoolWithdrawalPort,
  InsufficientCashPoolBalanceError,
  type CashPoolWithdrawalCommand,
} from '../../application/ledger/cash-pool-withdrawal.port';
import { toApiClientError } from '../api/api-client.error';
import { LedgerService } from '../api/generated';

@Injectable({ providedIn: 'root' })
export class HttpCashPoolWithdrawalGateway extends CashPoolWithdrawalPort {
  constructor(private readonly ledgerService: LedgerService) {
    super();
  }

  override async withdraw(command: CashPoolWithdrawalCommand): Promise<void> {
    await firstValueFrom(
      this.ledgerService
        .withdrawFromCashPool(command.groupId, {
          amountInCents: command.amountInCents,
          withdrawnAt: command.withdrawnAt.toISOString(),
        })
        .pipe(
          map(() => undefined),
          catchError((error) => {
            const apiError = toApiClientError(error, 'Le retrait n a pas pu etre enregistre.');
            if (apiError.status === 409) throw new InsufficientCashPoolBalanceError();
            throw apiError;
          }),
        ),
    );
  }
}
