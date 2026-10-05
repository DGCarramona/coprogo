import { Injectable } from '@angular/core';
import { catchError, firstValueFrom, map } from 'rxjs';

import {
  ExpenseSupportingDocumentDeletionPort,
  type DeleteExpenseSupportingDocumentCommand,
} from '../../application/supporting-document/expense-supporting-document-deletion.port';
import { SupportingDocumentsService } from '../api/generated';
import { toApiClientError } from '../api/api-client.error';

@Injectable({ providedIn: 'root' })
export class HttpExpenseSupportingDocumentDeletionGateway extends ExpenseSupportingDocumentDeletionPort {
  constructor(private readonly supportingDocumentsService: SupportingDocumentsService) {
    super();
  }

  override async delete(command: DeleteExpenseSupportingDocumentCommand): Promise<void> {
    await firstValueFrom(
      this.supportingDocumentsService
        .deleteExpenseSupportingDocument(
          command.groupId,
          command.expenseId,
          command.sourceUploadIntent,
        )
        .pipe(
          catchError((error) => {
            throw toApiClientError(error, 'Le justificatif n a pas pu etre retire.');
          }),
          map(() => undefined),
        ),
    );
  }
}
