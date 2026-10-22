import type { ExpenseDetail } from '../../domain/expense/expense-detail';

export abstract class ExpenseDetailPort {
  abstract get(groupId: string, expenseId: string): Promise<ExpenseDetail>;
}
