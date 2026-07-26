import { HttpClient } from '@angular/common/http';
import { Inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { GroupFinancialDashboardPort } from '../../application/ledger/group-financial-dashboard.port';
import { GroupFinancialDashboard } from '../../domain/ledger/group-financial-dashboard';
import { toApiClientError } from '../api/api-client.error';
import { API_BASE_PATH } from '../api/provide-api-client';

interface GroupBalancesResponseDto {
  group: string;
  balances: GroupMemberBalanceResponseDto[];
}

interface GroupMemberBalanceResponseDto {
  member: string;
  netAmountInCents: number;
}

interface CashPoolBalanceResponseDto {
  group: string;
  availableAmountInCents: number;
}

interface GroupMemberCashPoolSharesResponseDto {
  group: string;
  shares: MemberCashPoolShareResponseDto[];
}

interface MemberCashPoolShareResponseDto {
  member: string;
  amountInCents: number;
}

@Injectable({ providedIn: 'root' })
export class HttpGroupFinancialDashboardGateway extends GroupFinancialDashboardPort {
  constructor(
    private readonly httpClient: HttpClient,
    @Inject(API_BASE_PATH) private readonly basePath: string,
  ) {
    super();
  }

  override async get(groupId: string): Promise<GroupFinancialDashboard> {
    try {
      const [balances, cashPoolBalance, cashPoolShares] = await Promise.all([
        firstValueFrom(
          this.httpClient.get<GroupBalancesResponseDto>(
            `${this.basePath}/api/groups/${groupId}/balances`,
          ),
        ),
        firstValueFrom(
          this.httpClient.get<CashPoolBalanceResponseDto>(
            `${this.basePath}/api/groups/${groupId}/cash-pool/balance`,
          ),
        ),
        firstValueFrom(
          this.httpClient.get<GroupMemberCashPoolSharesResponseDto>(
            `${this.basePath}/api/groups/${groupId}/cash-pool/shares`,
          ),
        ),
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
