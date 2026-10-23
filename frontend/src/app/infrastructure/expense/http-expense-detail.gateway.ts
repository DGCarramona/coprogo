import { Injectable } from '@angular/core';
import { catchError, firstValueFrom, map } from 'rxjs';

import { ExpenseDetailPort } from '../../application/expense/expense-detail.port';
import type {
  ExpenseDetail,
  ExpenseParticipation,
  ExpenseParticipationStatus,
  ExpenseRefusal,
} from '../../domain/expense/expense-detail';
import type {
  ExpenseDetailParticipationResponseDto,
  ExpenseDetailResponseDto,
  ExpenseRefusalResponseDto,
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
  canDecideParticipation: response.canDecideParticipation,
  totalAmountInCents: response.totalAmountCents,
  createdAt: toExpenseCreationDate(response.createdAt),
  status: toExpenseStatus(response.status),
  refusal:
    response.refusal === null || response.refusal === undefined
      ? null
      : toRefusal(response.refusal),
  participations: response.participations.map(toExpenseParticipation),
});

const toRefusal = (response: ExpenseRefusalResponseDto): ExpenseRefusal => ({
  member: response.member,
  refusedAt: toExpenseRefusalDate(response.refusedAt),
  reason: response.reason ?? null,
});

const toExpenseRefusalDate = (value: string): Date => {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    throw new Error(`Date de refus de depense invalide: ${value}.`);
  }

  return date;
};

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
