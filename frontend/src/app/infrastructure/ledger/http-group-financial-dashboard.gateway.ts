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
        catchError((error) => {
          throw toApiClientError(error, 'Le dashboard financier n a pas pu etre charge.');
        }),
        map(({ balances, cashPoolBalance, cashPoolShares }) => ({
          groupId: balances.group,
          memberBalances: balances.balances,
          cashPoolBalance: {
            availableAmountInCents: cashPoolBalance.availableAmountInCents,
          },
          cashPoolShares: cashPoolShares.shares,
        })),
      ),
    );
  }
}
