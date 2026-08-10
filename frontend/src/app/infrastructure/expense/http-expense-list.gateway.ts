import { Injectable } from '@angular/core';
import { catchError, firstValueFrom } from 'rxjs';

import { ExpenseListPort } from '../../application/expense/expense-list.port';
import type { ExpenseStatus, ExpenseSummary } from '../../domain/expense/expense-summary';
import { mapArray } from '../../shared/rxjs/map-array';
import { ExpenseResponseDto, ExpensesService } from '../api/generated';
import { toApiClientError } from '../api/api-client.error';

@Injectable({ providedIn: 'root' })
export class HttpExpenseListGateway extends ExpenseListPort {
  constructor(private readonly expensesService: ExpensesService) {
    super();
  }

  override async listByGroup(groupId: string): Promise<readonly ExpenseSummary[]> {
    return await firstValueFrom(
      this.expensesService.listGroupExpenses(groupId).pipe(
        catchError((error) => {
          throw toApiClientError(error, 'Les depenses n ont pas pu etre chargees.');
        }),
        mapArray(toExpenseSummary),
      ),
    );
  }
}

const toExpenseSummary = (response: ExpenseResponseDto): ExpenseSummary => ({
  id: response.id,
  title: response.title,
  createdBy: response.createdBy,
  totalAmountInCents: response.totalAmountCents,
  createdAt: toCreationDate(response.createdAt),
  status: toExpenseStatus(response.status),
});

const toExpenseStatus = (status: string): ExpenseStatus => {
  switch (status) {
    case 'PROPOSED':
    case 'ACCEPTED':
    case 'INVALIDATED':
      return status;
    default:
      throw new Error(`Statut de depense inconnu: ${status}.`);
  }
};

const toCreationDate = (value: string): Date => {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    throw new Error(`Date de creation de depense invalide: ${value}.`);
  }

  return date;
};
