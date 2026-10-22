import { Injectable } from '@angular/core';
import { catchError, firstValueFrom } from 'rxjs';

import { ExpenseListPort } from '../../application/expense/expense-list.port';
import type { ExpenseSummary } from '../../domain/expense/expense-summary';
import { mapArray } from '../../shared/rxjs/map-array';
import { ExpenseResponseDto, ExpensesService } from '../api/generated';
import { toApiClientError } from '../api/api-client.error';
import { toExpenseCreationDate, toExpenseStatus } from './expense-response.mapper';

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
  createdAt: toExpenseCreationDate(response.createdAt),
  status: toExpenseStatus(response.status),
});
