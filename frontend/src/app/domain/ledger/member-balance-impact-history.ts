export type MemberBalanceImpactOperationType =
  'DIRECT_REIMBURSEMENT' | 'COMPENSATING_EXPENSE' | 'CASH_POOL_WITHDRAWAL';

export type MemberBalanceImpactType = 'DEBT_REDUCTION' | 'DEBT_INCREASE' | 'CREDIT_REDUCTION';

export interface MemberBalanceImpact {
  readonly type: MemberBalanceImpactOperationType;
  readonly effect: MemberBalanceImpactType;
  readonly operationId: string;
  readonly amountInCents: number;
  readonly occurredAt: Date;
}

export interface MemberBalanceImpactHistory {
  readonly groupId: string;
  readonly member: string;
  readonly impacts: readonly MemberBalanceImpact[];
}
