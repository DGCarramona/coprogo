export abstract class ExpenseParticipationDecisionPort {
  abstract approve(groupId: string, expenseId: string): Promise<void>;

  abstract refuse(groupId: string, expenseId: string, reason: string | null): Promise<void>;
}
