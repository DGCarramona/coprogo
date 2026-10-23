import { Injectable } from '@angular/core';
import { catchError, firstValueFrom, map } from 'rxjs';

import { MemberDebtReductionHistoryPort } from '../../application/ledger/member-debt-reduction-history.port';
import type {
  MemberDebtReduction,
  MemberDebtReductionHistory,
  MemberDebtReductionType,
} from '../../domain/ledger/member-debt-reduction-history';
import type {
  MemberDebtReductionHistoryResponseDto,
  MemberDebtReductionResponseDto,
  MemberDebtReductionTypeResponseDto,
} from '../api/generated';
import { LedgerService } from '../api/generated';
import { toApiClientError } from '../api/api-client.error';

@Injectable({ providedIn: 'root' })
export class HttpMemberDebtReductionHistoryGateway extends MemberDebtReductionHistoryPort {
  constructor(private readonly ledgerService: LedgerService) {
    super();
  }

  override async get(groupId: string, member: string): Promise<MemberDebtReductionHistory> {
    return await firstValueFrom(
      this.ledgerService.getMemberDebtReductionHistory(groupId, member).pipe(
        catchError((error) => {
          throw toApiClientError(
            error,
            'Les operations qui ont reduit cette dette n ont pas pu etre chargees.',
          );
        }),
        map(toMemberDebtReductionHistory),
      ),
    );
  }
}

const toMemberDebtReductionHistory = (
  response: MemberDebtReductionHistoryResponseDto,
): MemberDebtReductionHistory => ({
  groupId: response.group,
  member: response.member,
  reductions: response.reductions.map(toMemberDebtReduction),
});

const toMemberDebtReduction = (response: MemberDebtReductionResponseDto): MemberDebtReduction => ({
  type: toMemberDebtReductionType(response.type),
  operationId: response.operationId,
  amountInCents: response.amountInCents,
  occurredAt: toDate(response.occurredAt),
});

const toMemberDebtReductionType = (
  type: MemberDebtReductionTypeResponseDto | string,
): MemberDebtReductionType => {
  switch (type) {
    case 'DIRECT_REIMBURSEMENT':
    case 'COMPENSATING_EXPENSE':
    case 'CASH_POOL_WITHDRAWAL':
      return type;
    default:
      throw new Error(`Type de remboursement inconnu: ${String(type)}.`);
  }
};

const toDate = (value: string): Date => {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    throw new Error(`Date d operation invalide: ${value}.`);
  }
  return date;
};
