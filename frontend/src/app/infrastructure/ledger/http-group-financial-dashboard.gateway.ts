import { Injectable } from '@angular/core';
import { catchError, firstValueFrom, forkJoin, map } from 'rxjs';

import { GroupFinancialDashboardPort } from '../../application/ledger/group-financial-dashboard.port';
import type { GroupFinancialDashboard } from '../../domain/ledger/group-financial-dashboard';
import { LedgerService } from '../api/generated';
import { toApiClientError } from '../api/api-client.error';

@Injectable({ providedIn: 'root' })
export class HttpGroupFinancialDashboardGateway extends GroupFinancialDashboardPort {
  constructor(private readonly ledgerService: LedgerService) {
    super();
  }

  override async get(groupId: string): Promise<GroupFinancialDashboard> {
    return await firstValueFrom(
      forkJoin({
        balances: this.ledgerService.getGroupBalances(groupId),
        cashPoolBalance: this.ledgerService.getCashPoolBalance(groupId),
        cashPoolShares: this.ledgerService.getMemberCashPoolShares(groupId),
      }).pipe(
        map(({ balances, cashPoolBalance, cashPoolShares }) => {
          if (
            balances?.group !== groupId ||
            !Array.isArray(balances.balances) ||
            !Array.isArray(cashPoolShares?.shares) ||
            !Number.isSafeInteger(cashPoolBalance?.availableAmountInCents)
          ) {
            throw new Error('Invalid dashboard response');
          }

          return {
            groupId: balances.group,
            memberBalances: balances.balances,
            cashPoolBalance: {
              availableAmountInCents: cashPoolBalance.availableAmountInCents,
            },
            cashPoolShares: cashPoolShares.shares,
          };
        }),
        catchError((error) => {
          throw toApiClientError(error, 'errors.dashboard.load');
        }),
      ),
    );
  }
}
