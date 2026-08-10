export type ExpenseAllocation =
  | {
      readonly type: 'EQUAL';
      readonly participants: ReadonlySet<string>;
    }
  | {
      readonly type: 'EQUAL_WITH_CAPS';
      readonly participants: ReadonlySet<string>;
      readonly capsInCentsByMember: ReadonlyMap<string, number>;
    }
  | {
      readonly type: 'CUMULATIVE_TIERS';
      readonly tiers: readonly {
        readonly upToAmountInCents: number;
        readonly participants: ReadonlySet<string>;
      }[];
    }
  | {
      readonly type: 'CUSTOM';
      readonly amountsInCentsByMember: ReadonlyMap<string, number>;
    };

export interface ExpenseProposalCommand {
  readonly groupId: string;
  readonly title: string;
  readonly totalAmountInCents: number;
  readonly allocation: ExpenseAllocation;
}

export abstract class ExpenseProposalPort {
  abstract propose(command: ExpenseProposalCommand): Promise<void>;
}
