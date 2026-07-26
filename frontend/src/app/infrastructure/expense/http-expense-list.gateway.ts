import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { ExpenseListPort } from '../../application/expense/expense-list.port';
import { ExpensesService } from '../api/generated';
import { toApiClientError } from '../api/api-client.error';

@Injectable({ providedIn: 'root' })
export class HttpExpenseListGateway extends ExpenseListPort {
  constructor(private readonly expensesService: ExpensesService) {
    super();
  }

  override async listByGroup(groupId: string) {
    try {
      return await firstValueFrom(this.expensesService.listGroupExpenses(groupId));
    } catch (error) {
      throw toApiClientError(error, 'Les depenses n ont pas pu etre chargees.');
    }
  }
}
