import { Injectable } from '@angular/core';
import { catchError, firstValueFrom, map } from 'rxjs';

import { ExpenseParticipationDecisionPort } from '../../application/expense/expense-participation-decision.port';
import { ExpensesService } from '../api/generated';
import { toApiClientError } from '../api/api-client.error';

@Injectable({ providedIn: 'root' })
export class HttpExpenseParticipationDecisionGateway extends ExpenseParticipationDecisionPort {
  constructor(private readonly expensesService: ExpensesService) {
    super();
  }

  override async approve(groupId: string, expenseId: string): Promise<void> {
    await this.record(groupId, expenseId, { decision: 'APPROVE' });
  }

  override async refuse(groupId: string, expenseId: string, reason: string | null): Promise<void> {
    await this.record(groupId, expenseId, { decision: 'REFUSE', reason });
  }

  private async record(
    groupId: string,
    expenseId: string,
    request:
      | { readonly decision: 'APPROVE' }
      | { readonly decision: 'REFUSE'; readonly reason: string | null },
  ): Promise<void> {
    await firstValueFrom(
      this.expensesService.recordParticipationDecision(groupId, expenseId, request).pipe(
        catchError((error) => {
          throw toApiClientError(
            error,
            'La decision sur la participation n a pas pu etre enregistree.',
          );
        }),
        map(() => undefined),
      ),
    );
  }
}
