import type { ExpenseStatus } from './expense-summary';

export type ExpenseParticipationStatus = 'PENDING' | 'APPROVED' | 'REFUSED';

export interface ExpenseParticipation {
  readonly member: string;
  readonly amountInCents: number;
  readonly status: ExpenseParticipationStatus;
}

export interface ExpenseRefusal {
  readonly member: string;
  readonly refusedAt: Date;
  readonly reason: string | null;
}

export interface ExpenseDetail {
  readonly id: string;
  readonly title: string;
  readonly createdBy: string;
  readonly canDecideParticipation: boolean;
  readonly totalAmountInCents: number;
  readonly createdAt: Date;
  readonly status: ExpenseStatus;
  readonly refusal: ExpenseRefusal | null;
  readonly participations: readonly ExpenseParticipation[];
}
