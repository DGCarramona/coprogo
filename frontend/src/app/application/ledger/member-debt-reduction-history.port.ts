import type { MemberDebtReductionHistory } from '../../domain/ledger/member-debt-reduction-history';

export abstract class MemberDebtReductionHistoryPort {
  abstract get(groupId: string, member: string): Promise<MemberDebtReductionHistory>;
}
