import { Injectable } from '@angular/core';
import { catchError, firstValueFrom } from 'rxjs';

import { ReimbursementListPort } from '../../application/reimbursement/reimbursement-list.port';
import type {
  ReimbursementDecision,
  ReimbursementSummary,
  ReimbursementSupportingDocument,
} from '../../domain/reimbursement/reimbursement-summary';
import { mapArray } from '../../shared/rxjs/map-array';
import type {
  ReimbursementResponseDto,
  ReimbursementStatusResponseDto,
  ReimbursementSupportingDocumentResponseDto,
} from '../api/generated';
import { ReimbursementsService } from '../api/generated';
import { toApiClientError } from '../api/api-client.error';

@Injectable({ providedIn: 'root' })
export class HttpReimbursementListGateway extends ReimbursementListPort {
  constructor(private readonly reimbursementsService: ReimbursementsService) {
    super();
  }

  override async listByGroup(groupId: string): Promise<readonly ReimbursementSummary[]> {
    return await firstValueFrom(
      this.reimbursementsService.listReimbursements(groupId).pipe(
        catchError((error) => {
          throw toApiClientError(error, 'errors.reimbursements.loadHistory');
        }),
        mapArray(toReimbursementSummary),
      ),
    );
  }
}

const toReimbursementSummary = (response: ReimbursementResponseDto): ReimbursementSummary => ({
  id: response.id,
  paidBy: response.paidBy,
  receivedBy: response.receivedBy,
  amountInCents: response.amountInCents,
  reimbursedAt: toDate(response.reimbursedAt, 'errors.reimbursements.invalidDate'),
  declaredBy: response.declaredBy,
  declaredAt: toDate(response.declaredAt, 'errors.reimbursements.invalidDeclarationDate'),
  documents: response.documents.map(toSupportingDocument),
  ...toDecision(response),
});

const toDecision = (response: ReimbursementResponseDto): ReimbursementDecision => {
  switch (toStatus(response.status)) {
    case 'PENDING_REVIEW':
      if (response.decidedAt != null || response.rejectionReason != null) {
        throw new Error('errors.reimbursements.inconsistentReview');
      }
      return { status: 'PENDING_REVIEW', decidedAt: null, rejectionReason: null };
    case 'ACCEPTED':
      return {
        status: 'ACCEPTED',
        decidedAt: toRequiredDecisionDate(response.decidedAt),
        rejectionReason: null,
      };
    case 'REJECTED':
      return {
        status: 'REJECTED',
        decidedAt: toRequiredDecisionDate(response.decidedAt),
        rejectionReason: response.rejectionReason ?? null,
      };
  }
};

const toStatus = (
  status: ReimbursementStatusResponseDto | string,
): ReimbursementSummary['status'] => {
  switch (status) {
    case 'PENDING_REVIEW':
    case 'ACCEPTED':
    case 'REJECTED':
      return status;
    default:
      throw new Error(`Statut de remboursement inconnu: ${String(status)}.`);
  }
};

const toRequiredDecisionDate = (value: string | null | undefined): Date => {
  if (value == null) {
    throw new Error('errors.reimbursements.missingReviewDate');
  }
  return toDate(value, 'errors.reimbursements.invalidReviewDate');
};

const toSupportingDocument = (
  response: ReimbursementSupportingDocumentResponseDto,
): ReimbursementSupportingDocument => ({
  sourceUploadIntent: response.sourceUploadIntent,
  fileName: response.fileName,
  mediaType: response.mediaType,
  sizeBytes: response.sizeBytes,
  uploader: response.uploader,
  attachedAt: toDate(response.attachedAt, 'errors.documents.invalidAttachmentDate'),
  downloadTarget: {
    url: response.download.url,
    expiresAt: toDate(response.download.expiresAt, 'errors.documents.invalidDownloadExpiration'),
  },
});

const toDate = (value: string, message: string): Date => {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    throw new Error(`${message}: ${value}.`);
  }
  return date;
};
