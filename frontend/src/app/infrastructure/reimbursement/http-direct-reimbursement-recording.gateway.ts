import { Injectable } from '@angular/core';
import { catchError, firstValueFrom, map } from 'rxjs';

import {
  DirectReimbursementRecordingPort,
  type DirectReimbursementRecordingCommand,
} from '../../application/reimbursement/direct-reimbursement-recording.port';
import type { DirectReimbursementRequestDto } from '../api/generated';
import { ReimbursementsService } from '../api/generated';
import { toApiClientError } from '../api/api-client.error';

@Injectable({ providedIn: 'root' })
export class HttpDirectReimbursementRecordingGateway extends DirectReimbursementRecordingPort {
  constructor(private readonly reimbursementsService: ReimbursementsService) {
    super();
  }

  override async record(command: DirectReimbursementRecordingCommand): Promise<void> {
    await firstValueFrom(
      this.reimbursementsService
        .recordDirectReimbursement(command.groupId, toRequest(command))
        .pipe(
          catchError((error) => {
            throw toApiClientError(error, 'Le remboursement n a pas pu etre enregistre.');
          }),
          map(() => undefined),
        ),
    );
  }
}

const toRequest = (
  command: DirectReimbursementRecordingCommand,
): DirectReimbursementRequestDto => ({
  paidBy: command.paidBy,
  amountInCents: command.amountInCents,
  reimbursedAt: command.reimbursedAt.toISOString(),
});
