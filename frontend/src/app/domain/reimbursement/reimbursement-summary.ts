export interface ReimbursementDocumentDownloadTarget {
  readonly url: string;
  readonly expiresAt: Date;
}

export interface ReimbursementSupportingDocument {
  readonly sourceUploadIntent: string;
  readonly fileName: string;
  readonly mediaType: string;
  readonly sizeBytes: number;
  readonly uploader: string;
  readonly attachedAt: Date;
  readonly downloadTarget: ReimbursementDocumentDownloadTarget;
}

interface ReimbursementSummaryFields {
  readonly id: string;
  readonly paidBy: string;
  readonly receivedBy: string;
  readonly amountInCents: number;
  readonly reimbursedAt: Date;
  readonly declaredBy: string;
  readonly declaredAt: Date;
  readonly documents: readonly ReimbursementSupportingDocument[];
}

export type ReimbursementDecision =
  | {
      readonly status: 'PENDING_REVIEW';
      readonly decidedAt: null;
      readonly rejectionReason: null;
    }
  | {
      readonly status: 'ACCEPTED';
      readonly decidedAt: Date;
      readonly rejectionReason: null;
    }
  | {
      readonly status: 'REJECTED';
      readonly decidedAt: Date;
      readonly rejectionReason: string | null;
    };

export type ReimbursementSummary = ReimbursementSummaryFields & ReimbursementDecision;
