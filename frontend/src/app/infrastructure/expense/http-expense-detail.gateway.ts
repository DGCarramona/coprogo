import { Injectable } from '@angular/core';
import { catchError, firstValueFrom, map } from 'rxjs';

import { ExpenseDetailPort } from '../../application/expense/expense-detail.port';
import type {
  ExpenseDetail,
  ExpenseParticipation,
  ExpenseParticipationStatus,
} from '../../domain/expense/expense-detail';
import type {
  ExpenseDetailParticipationResponseDto,
  ExpenseDetailResponseDto,
} from '../api/generated';
import { ExpensesService } from '../api/generated';
import { toApiClientError } from '../api/api-client.error';
import { toExpenseCreationDate, toExpenseStatus } from './expense-response.mapper';

@Injectable({ providedIn: 'root' })
export class HttpExpenseDetailGateway extends ExpenseDetailPort {
  constructor(private readonly expensesService: ExpensesService) {
    super();
  }

  override async get(groupId: string, expenseId: string): Promise<ExpenseDetail> {
    return await firstValueFrom(
      this.expensesService.getExpenseDetail(groupId, expenseId).pipe(
        catchError((error) => {
          throw toApiClientError(error, 'La depense n a pas pu etre chargee.');
        }),
        map(toExpenseDetail),
      ),
    );
  }
}

const toExpenseDetail = (response: ExpenseDetailResponseDto): ExpenseDetail => ({
  id: response.id,
  title: response.title,
  createdBy: response.createdBy,
  totalAmountInCents: response.totalAmountCents,
  createdAt: toExpenseCreationDate(response.createdAt),
  status: toExpenseStatus(response.status),
  participations: response.participations.map(toExpenseParticipation),
});

const toExpenseParticipation = (
  response: ExpenseDetailParticipationResponseDto,
): ExpenseParticipation => ({
  member: response.member,
  amountInCents: response.amountCents,
  status: toExpenseParticipationStatus(response.status),
});

const toExpenseParticipationStatus = (status: string): ExpenseParticipationStatus => {
  switch (status) {
    case 'PENDING':
    case 'APPROVED':
    case 'REFUSED':
      return status;
    default:
      throw new Error(`Statut de participation inconnu: ${status}.`);
  }
};
