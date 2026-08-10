import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import {
  ExpenseProposalPort,
  ProposeEqualSplitExpenseCommand,
} from '../../application/expense/expense-proposal.port';
import { ExpensesService } from '../api/generated';
import { toApiClientError } from '../api/api-client.error';

@Injectable({ providedIn: 'root' })
export class HttpExpenseProposalGateway extends ExpenseProposalPort {
  constructor(private readonly expensesService: ExpensesService) {
    super();
  }

  override async proposeEqualSplit(command: ProposeEqualSplitExpenseCommand): Promise<void> {
    try {
      await firstValueFrom(
        this.expensesService.proposeExpense(command.groupId, {
          title: command.title,
          totalAmountInCents: command.totalAmountInCents,
          allocation: {
            type: 'EQUAL',
            participants: [...command.participants],
          },
        }),
      );
    } catch (error) {
      throw toApiClientError(error, 'La depense n a pas pu etre proposee.');
    }
  }
}
