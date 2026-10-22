import type { RevenueDistributionPreview } from '../../domain/revenue/revenue-distribution-preview';

export interface RevenueDistributionPreviewQuery {
  readonly groupId: string;
  readonly amountInCents: number;
  readonly effectiveDate: Date;
}

export abstract class RevenueDistributionPreviewPort {
  abstract preview(query: RevenueDistributionPreviewQuery): Promise<RevenueDistributionPreview>;
}
