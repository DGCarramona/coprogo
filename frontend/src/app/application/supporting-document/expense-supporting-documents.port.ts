export interface ExpenseSupportingDocumentDeletion {
  readonly deletedBy: string;
  readonly deletedAt: Date;
}

export interface ExpenseSupportingDocumentDownloadTarget {
  readonly url: string;
  readonly expiresAt: Date;
}

export interface ExpenseSupportingDocument {
  readonly sourceUploadIntent: string;
  readonly fileName: string;
  readonly mediaType: string;
  readonly sizeBytes: number;
  readonly uploader: string;
  readonly attachedAt: Date;
  readonly replacesSourceUploadIntent: string | null;
  readonly deletion: ExpenseSupportingDocumentDeletion | null;
  readonly canDelete: boolean;
  readonly downloadTarget: ExpenseSupportingDocumentDownloadTarget;
}

export interface ExpenseSupportingDocuments {
  readonly current: readonly ExpenseSupportingDocument[];
  readonly history: readonly ExpenseSupportingDocument[];
}

export abstract class ExpenseSupportingDocumentsPort {
  abstract listByExpense(groupId: string, expenseId: string): Promise<ExpenseSupportingDocuments>;
}
