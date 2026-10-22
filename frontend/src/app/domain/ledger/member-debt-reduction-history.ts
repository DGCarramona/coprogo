export type MemberDebtReductionType =
  'DIRECT_REIMBURSEMENT' | 'COMPENSATING_EXPENSE' | 'CASH_POOL_WITHDRAWAL';

export interface MemberDebtReduction {
  readonly type: MemberDebtReductionType;
  readonly operationId: string;
  readonly amountInCents: number;
  readonly occurredAt: Date;
}

export interface MemberDebtReductionHistory {
  readonly groupId: string;
  readonly member: string;
  readonly reductions: readonly MemberDebtReduction[];
}
