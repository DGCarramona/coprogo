import { Injectable } from '@angular/core';
import { catchError, firstValueFrom, map } from 'rxjs';

import {
  ExpenseSupportingDocumentsPort,
  type ExpenseSupportingDocument,
  type ExpenseSupportingDocuments,
} from '../../application/supporting-document/expense-supporting-documents.port';
import type {
  ExpenseSupportingDocumentResponseDto,
  ExpenseSupportingDocumentsResponseDto,
} from '../api/generated';
import { SupportingDocumentsService } from '../api/generated';
import { toApiClientError } from '../api/api-client.error';

@Injectable({ providedIn: 'root' })
export class HttpExpenseSupportingDocumentsGateway extends ExpenseSupportingDocumentsPort {
  constructor(private readonly supportingDocumentsService: SupportingDocumentsService) {
    super();
  }

  override async listByExpense(
    groupId: string,
    expenseId: string,
  ): Promise<ExpenseSupportingDocuments> {
    return await firstValueFrom(
      this.supportingDocumentsService.listExpenseSupportingDocuments(groupId, expenseId).pipe(
        catchError((error) => {
          throw toApiClientError(error, 'errors.documents.load');
        }),
        map(toExpenseSupportingDocuments),
      ),
    );
  }
}

const toExpenseSupportingDocuments = (
  response: ExpenseSupportingDocumentsResponseDto,
): ExpenseSupportingDocuments => ({
  current: response.current.map(toExpenseSupportingDocument),
  history: response.history.map(toExpenseSupportingDocument),
});

const toExpenseSupportingDocument = (
  response: ExpenseSupportingDocumentResponseDto,
): ExpenseSupportingDocument => ({
  sourceUploadIntent: response.sourceUploadIntent,
  fileName: response.fileName,
  mediaType: response.mediaType,
  sizeBytes: response.sizeBytes,
  uploader: response.uploader,
  attachedAt: toDate(response.attachedAt, 'errors.documents.invalidAttachmentDate'),
  replacesSourceUploadIntent: response.replacesSourceUploadIntent ?? null,
  deletion:
    response.deletion === null || response.deletion === undefined
      ? null
      : {
          deletedBy: response.deletion.deletedBy,
          deletedAt: toDate(response.deletion.deletedAt, 'errors.documents.invalidRemovalDate'),
        },
  canDelete: response.canDelete,
  downloadTarget: {
    url: response.download.url,
    expiresAt: toDate(response.download.expiresAt, 'errors.documents.invalidLinkExpiration'),
  },
});

const toDate = (value: string, message: string): Date => {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    throw new Error(`${message}: ${value}.`);
  }

  return date;
};
