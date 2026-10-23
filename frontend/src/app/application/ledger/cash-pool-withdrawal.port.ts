export interface CashPoolWithdrawalCommand {
  readonly groupId: string;
  readonly amountInCents: number;
  readonly withdrawnAt: Date;
}

export class InsufficientCashPoolBalanceError extends Error {
  constructor(message = 'Le montant dépasse ce qui est disponible dans la caisse commune.') {
    super(message);
  }
}

export abstract class CashPoolWithdrawalPort {
  abstract withdraw(command: CashPoolWithdrawalCommand): Promise<void>;
}
