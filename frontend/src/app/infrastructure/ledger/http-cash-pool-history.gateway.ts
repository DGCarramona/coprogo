import { Injectable } from '@angular/core';
import { catchError, firstValueFrom, map } from 'rxjs';

import { CashPoolHistoryPort } from '../../application/ledger/cash-pool-history.port';
import type { CashPoolHistory, CashPoolOperation } from '../../domain/ledger/cash-pool-history';
import { toApiClientError } from '../api/api-client.error';
import { type CashPoolOperationResponseDto, LedgerService } from '../api/generated';

@Injectable({ providedIn: 'root' })
export class HttpCashPoolHistoryGateway extends CashPoolHistoryPort {
  constructor(private readonly ledgerService: LedgerService) {
    super();
  }

  override get(groupId: string): Promise<CashPoolHistory> {
    return firstValueFrom(
      this.ledgerService.getCashPoolHistory(groupId).pipe(
        map((response) => ({
          groupId: response.group,
          operations: response.operations.map(toOperation),
        })),
        catchError((error) => {
          throw toApiClientError(
            error,
            'L’historique de la caisse commune n’a pas pu être chargé.',
          );
        }),
      ),
    );
  }
}

const toOperation = (response: CashPoolOperationResponseDto): CashPoolOperation => {
  switch (response.type) {
    case 'INCOME':
      return {
        type: response.type,
        operationId: response.operation,
        occurredAt: new Date(response.occurredAt),
        amountInCents: response.amountInCents,
        allocations: response.allocations,
      };
    case 'WITHDRAWAL':
      return {
        type: response.type,
        operationId: response.operation,
        occurredAt: new Date(response.occurredAt),
        withdrawnBy: response.withdrawnBy,
        amountInCents: response.amountInCents,
        ownRevenueShareConsumedInCents: response.ownRevenueShareConsumedInCents,
      };
  }
};
