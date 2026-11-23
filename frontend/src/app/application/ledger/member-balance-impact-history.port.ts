import type { MemberBalanceImpactHistory } from '../../domain/ledger/member-balance-impact-history';

export abstract class MemberBalanceImpactHistoryPort {
  abstract get(groupId: string, member: string): Promise<MemberBalanceImpactHistory>;
}
