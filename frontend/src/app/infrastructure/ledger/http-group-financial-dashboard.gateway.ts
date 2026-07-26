import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { GroupFinancialDashboardPort } from '../../application/ledger/group-financial-dashboard.port';
import { GroupFinancialDashboard } from '../../domain/ledger/group-financial-dashboard';
import { LedgerService } from '../api/generated';
import { toApiClientError } from '../api/api-client.error';

@Injectable({ providedIn: 'root' })
export class HttpGroupFinancialDashboardGateway extends GroupFinancialDashboardPort {
  constructor(private readonly ledgerService: LedgerService) {
    super();
  }

  override async get(groupId: string): Promise<GroupFinancialDashboard> {
    try {
      const [balances, cashPoolBalance, cashPoolShares] = await Promise.all([
        firstValueFrom(this.ledgerService.getGroupBalances({ groupId })),
        firstValueFrom(this.ledgerService.getCashPoolBalance({ groupId })),
        firstValueFrom(this.ledgerService.getMemberCashPoolShares({ groupId })),
      ]);

      return {
        groupId: balances.group,
        memberBalances: balances.balances,
        cashPoolBalance: {
          availableAmountInCents: cashPoolBalance.availableAmountInCents,
        },
        cashPoolShares: cashPoolShares.shares,
      };
    } catch (error) {
      throw toApiClientError(error, 'Le dashboard financier n a pas pu etre charge.');
    }
  }
}
