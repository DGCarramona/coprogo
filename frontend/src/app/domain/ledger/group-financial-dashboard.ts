export interface GroupFinancialDashboard {
  readonly groupId: string;
  readonly memberBalances: readonly GroupMemberBalance[];
  readonly cashPoolBalance: CashPoolBalance;
  readonly cashPoolShares: readonly MemberCashPoolShare[];
}

export interface GroupMemberBalance {
  readonly member: string;
  readonly netAmountInCents: number;
}

export interface CashPoolBalance {
  readonly availableAmountInCents: number;
}

export interface MemberCashPoolShare {
  readonly member: string;
  readonly amountInCents: number;
}
