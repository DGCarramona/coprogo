export interface CashPoolIncomeRecordingCommand {
  readonly groupId: string;
  readonly amountInCents: number;
  readonly receivedAt: Date;
  readonly effectiveDate: Date;
}

export abstract class CashPoolIncomeRecordingPort {
  abstract record(command: CashPoolIncomeRecordingCommand): Promise<void>;
}
