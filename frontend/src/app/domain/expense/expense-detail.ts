import type { ExpenseStatus } from './expense-summary';

export type ExpenseParticipationStatus = 'PENDING' | 'APPROVED' | 'REFUSED';

export interface ExpenseParticipation {
  readonly member: string;
  readonly amountInCents: number;
  readonly status: ExpenseParticipationStatus;
}

export interface ExpenseDetail {
  readonly id: string;
  readonly title: string;
  readonly createdBy: string;
  readonly totalAmountInCents: number;
  readonly createdAt: Date;
  readonly status: ExpenseStatus;
  readonly participations: readonly ExpenseParticipation[];
}
