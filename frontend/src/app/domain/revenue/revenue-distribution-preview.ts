export interface RevenueDistributionPreview {
  readonly groupId: string;
  readonly effectiveDate: Date;
  readonly totalAmountInCents: number;
  readonly allocations: readonly RevenueDistributionAllocation[];
}

export interface RevenueDistributionAllocation {
  readonly member: string;
  readonly amountInCents: number;
}
