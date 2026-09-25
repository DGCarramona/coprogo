export interface DeleteExpenseSupportingDocumentCommand {
  readonly groupId: string;
  readonly expenseId: string;
  readonly sourceUploadIntent: string;
}

export abstract class ExpenseSupportingDocumentDeletionPort {
  abstract delete(command: DeleteExpenseSupportingDocumentCommand): Promise<void>;
}
