export interface ProposeEqualSplitExpenseCommand {
  groupId: string;
  title: string;
  totalAmountInCents: number;
  participants: ReadonlySet<string>;
}

export abstract class ExpenseProposalPort {
  abstract proposeEqualSplit(command: ProposeEqualSplitExpenseCommand): Promise<void>;
}
