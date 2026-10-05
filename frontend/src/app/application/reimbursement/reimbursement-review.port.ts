export abstract class ReimbursementReviewPort {
  abstract accept(groupId: string, reimbursementId: string): Promise<void>;

  abstract reject(groupId: string, reimbursementId: string, reason: string | null): Promise<void>;
}
