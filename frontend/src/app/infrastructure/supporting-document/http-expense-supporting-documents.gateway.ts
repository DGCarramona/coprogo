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
          throw toApiClientError(error, 'Les justificatifs n ont pas pu etre charges.');
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
  attachedAt: toDate(response.attachedAt, 'Date d ajout du justificatif invalide'),
  replacesSourceUploadIntent: response.replacesSourceUploadIntent ?? null,
  deletion:
    response.deletion === null || response.deletion === undefined
      ? null
      : {
          deletedBy: response.deletion.deletedBy,
          deletedAt: toDate(
            response.deletion.deletedAt,
            'Date de retrait du justificatif invalide',
          ),
        },
  canDelete: response.canDelete,
  downloadTarget: {
    url: response.download.url,
    expiresAt: toDate(response.download.expiresAt, 'Date d expiration du lien invalide'),
  },
});

const toDate = (value: string, message: string): Date => {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    throw new Error(`${message}: ${value}.`);
  }

  return date;
};
