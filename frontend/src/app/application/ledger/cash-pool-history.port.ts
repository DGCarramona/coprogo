import type { CashPoolHistory } from '../../domain/ledger/cash-pool-history';

export abstract class CashPoolHistoryPort {
  abstract get(groupId: string): Promise<CashPoolHistory>;
}
