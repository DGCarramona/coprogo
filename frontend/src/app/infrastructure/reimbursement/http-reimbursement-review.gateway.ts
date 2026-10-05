import { Injectable } from '@angular/core';
import { catchError, firstValueFrom, map } from 'rxjs';

import { ReimbursementReviewPort } from '../../application/reimbursement/reimbursement-review.port';
import { ReimbursementsService } from '../api/generated';
import { toApiClientError } from '../api/api-client.error';

@Injectable({ providedIn: 'root' })
export class HttpReimbursementReviewGateway extends ReimbursementReviewPort {
  constructor(private readonly reimbursementsService: ReimbursementsService) {
    super();
  }

  override async accept(groupId: string, reimbursementId: string): Promise<void> {
    await firstValueFrom(
      this.reimbursementsService.acceptReimbursement(groupId, reimbursementId).pipe(
        catchError((error) => {
          throw toApiClientError(error, 'Le remboursement n a pas pu etre accepte.');
        }),
        map(() => undefined),
      ),
    );
  }

  override async reject(
    groupId: string,
    reimbursementId: string,
    reason: string | null,
  ): Promise<void> {
    await firstValueFrom(
      this.reimbursementsService.rejectReimbursement(groupId, reimbursementId, { reason }).pipe(
        catchError((error) => {
          throw toApiClientError(error, 'Le remboursement n a pas pu etre refuse.');
        }),
        map(() => undefined),
      ),
    );
  }
}
