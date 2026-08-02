export type ExpenseStatus = 'PROPOSED' | 'ACCEPTED' | 'INVALIDATED';

export interface ExpenseSummary {
  readonly id: string;
  readonly title: string;
  readonly createdBy: string;
  readonly totalAmountInCents: number;
  readonly createdAt: Date;
  readonly status: ExpenseStatus;
}
