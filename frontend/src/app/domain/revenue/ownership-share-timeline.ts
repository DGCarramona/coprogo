export interface OwnershipShare {
  readonly member: string;
  readonly percentage: number;
}

export interface OwnershipShareChange {
  readonly id: string;
  readonly effectiveDate: Date;
  readonly recordedBy: string;
  readonly recordedAt: Date;
  readonly shares: readonly OwnershipShare[];
}

export interface OwnershipShareTimeline {
  readonly groupId: string;
  readonly changes: readonly OwnershipShareChange[];
}
