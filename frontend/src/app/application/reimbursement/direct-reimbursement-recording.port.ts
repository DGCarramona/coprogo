export interface DirectReimbursementRecordingCommand {
  readonly groupId: string;
  readonly paidBy: string;
  readonly amountInCents: number;
  readonly reimbursedAt: Date;
}

export abstract class DirectReimbursementRecordingPort {
  abstract record(command: DirectReimbursementRecordingCommand): Promise<void>;
}
