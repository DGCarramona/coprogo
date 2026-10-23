import type { ReimbursementSummary } from '../../domain/reimbursement/reimbursement-summary';

export abstract class ReimbursementListPort {
  abstract listByGroup(groupId: string): Promise<readonly ReimbursementSummary[]>;
}
