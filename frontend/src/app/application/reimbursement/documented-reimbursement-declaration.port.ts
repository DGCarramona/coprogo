export interface DocumentedReimbursementDeclarationCommand {
  readonly groupId: string;
  readonly receivedBy: string;
  readonly amountInCents: number;
  readonly reimbursedAt: Date;
  readonly supportingDocumentUploadIntents: ReadonlySet<string>;
}

export abstract class DocumentedReimbursementDeclarationPort {
  abstract declare(command: DocumentedReimbursementDeclarationCommand): Promise<void>;
}
