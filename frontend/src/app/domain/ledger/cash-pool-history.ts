export interface CashPoolIncomeAllocation {
  readonly member: string;
  readonly amountInCents: number;
}

export interface CashPoolIncome {
  readonly type: 'INCOME';
  readonly operationId: string;
  readonly occurredAt: Date;
  readonly amountInCents: number;
  readonly allocations: readonly CashPoolIncomeAllocation[];
}

export interface CashPoolWithdrawal {
  readonly type: 'WITHDRAWAL';
  readonly operationId: string;
  readonly occurredAt: Date;
  readonly withdrawnBy: string;
  readonly amountInCents: number;
  readonly ownRevenueShareConsumedInCents: number;
}

export type CashPoolOperation = CashPoolIncome | CashPoolWithdrawal;

export interface CashPoolHistory {
  readonly groupId: string;
  readonly operations: readonly CashPoolOperation[];
}
