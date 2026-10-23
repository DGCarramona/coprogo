import { Injectable } from '@angular/core';
import { catchError, firstValueFrom, map } from 'rxjs';

import {
  DocumentedReimbursementDeclarationPort,
  type DocumentedReimbursementDeclarationCommand,
} from '../../application/reimbursement/documented-reimbursement-declaration.port';
import { DocumentedReimbursementRequestDto, ReimbursementsService } from '../api/generated';
import { toApiClientError } from '../api/api-client.error';

@Injectable({ providedIn: 'root' })
export class HttpDocumentedReimbursementDeclarationGateway extends DocumentedReimbursementDeclarationPort {
  constructor(private readonly reimbursementsService: ReimbursementsService) {
    super();
  }

  override async declare(command: DocumentedReimbursementDeclarationCommand): Promise<void> {
    await firstValueFrom(
      this.reimbursementsService
        .declareDocumentedReimbursement(command.groupId, toRequest(command))
        .pipe(
          catchError((error) => {
            throw toApiClientError(error, 'Le remboursement n a pas pu etre declare.');
          }),
          map(() => undefined),
        ),
    );
  }
}

const toRequest = (
  command: DocumentedReimbursementDeclarationCommand,
): DocumentedReimbursementRequestDto => ({
  receivedBy: command.receivedBy,
  amountInCents: command.amountInCents,
  reimbursedAt: command.reimbursedAt.toISOString(),
  supportingDocumentUploadIntents: [...command.supportingDocumentUploadIntents],
});
