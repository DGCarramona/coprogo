import { Injectable } from '@angular/core';
import { catchError, firstValueFrom, map } from 'rxjs';
import { match } from 'ts-pattern';

import {
  ExpenseProposalPort,
  ExpenseProposalCommand,
} from '../../application/expense/expense-proposal.port';
import {
  ExpenseAllocationRequestDto,
  ExpensesService,
  ProposeExpenseRequestDto,
} from '../api/generated';
import { toApiClientError } from '../api/api-client.error';

@Injectable({ providedIn: 'root' })
export class HttpExpenseProposalGateway extends ExpenseProposalPort {
  constructor(private readonly expensesService: ExpensesService) {
    super();
  }

  override async propose(command: ExpenseProposalCommand): Promise<void> {
    await firstValueFrom(
      this.expensesService.proposeExpense(command.groupId, toRequest(command)).pipe(
        catchError((error) => {
          throw toApiClientError(error, 'La depense n a pas pu etre proposee.');
        }),
        map(() => undefined),
      ),
    );
  }
}

const toRequest = (command: ExpenseProposalCommand): ProposeExpenseRequestDto => ({
  title: command.title,
  totalAmountInCents: command.totalAmountInCents,
  allocation: match(command.allocation)
    .returnType<ExpenseAllocationRequestDto>()
    .with({ type: 'EQUAL' }, (equal) => ({
      type: 'EQUAL',
      participants: [...equal.participants],
    }))
    .with({ type: 'EQUAL_WITH_CAPS' }, (equalWithCaps) => ({
      type: 'EQUAL_WITH_CAPS',
      participants: [...equalWithCaps.participants],
      caps: [...equalWithCaps.capsInCentsByMember].map(([member, maximumAmountInCents]) => ({
        member,
        maximumAmountInCents,
      })),
    }))
    .with({ type: 'CUMULATIVE_TIERS' }, (cumulativeTiers) => ({
      type: 'CUMULATIVE_TIERS',
      tiers: cumulativeTiers.tiers.map((tier) => ({
        upToAmountInCents: tier.upToAmountInCents,
        participants: [...tier.participants],
      })),
    }))
    .with({ type: 'CUSTOM' }, (custom) => ({
      type: 'CUSTOM',
      participations: [...custom.amountsInCentsByMember].map(([member, amountInCents]) => ({
        member,
        amountInCents,
      })),
    }))
    .exhaustive(),
});
