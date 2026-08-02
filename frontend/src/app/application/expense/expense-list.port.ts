import type { ExpenseSummary } from '../../domain/expense/expense-summary';

export abstract class ExpenseListPort {
  abstract listByGroup(groupId: string): Promise<readonly ExpenseSummary[]>;
}
