export interface OwnershipShareInput {
  readonly member: string;
  readonly percentage: number;
}

export interface OwnershipShareChangeCommand {
  readonly groupId: string;
  readonly effectiveDate: Date;
  readonly shares: readonly OwnershipShareInput[];
}

export abstract class OwnershipShareChangePort {
  abstract record(command: OwnershipShareChangeCommand): Promise<void>;
}
