import { Injectable } from '@angular/core';
import { catchError, firstValueFrom, map } from 'rxjs';

import { MemberBalanceImpactHistoryPort } from '../../application/ledger/member-balance-impact-history.port';
import type {
  MemberBalanceImpactType,
  MemberBalanceImpact,
  MemberBalanceImpactHistory,
  MemberBalanceImpactOperationType,
} from '../../domain/ledger/member-balance-impact-history';
import type {
  MemberBalanceImpactTypeResponseDto,
  MemberBalanceImpactHistoryResponseDto,
  MemberBalanceImpactResponseDto,
  MemberBalanceImpactOperationTypeResponseDto,
} from '../api/generated';
import { LedgerService } from '../api/generated';
import { toApiClientError } from '../api/api-client.error';

@Injectable({ providedIn: 'root' })
export class HttpMemberBalanceImpactHistoryGateway extends MemberBalanceImpactHistoryPort {
  constructor(private readonly ledgerService: LedgerService) {
    super();
  }

  override async get(groupId: string, member: string): Promise<MemberBalanceImpactHistory> {
    return await firstValueFrom(
      this.ledgerService.getMemberBalanceImpactHistory(groupId, member).pipe(
        catchError((error) => {
          throw toApiClientError(error, 'errors.balanceImpact.loadHistory');
        }),
        map(toMemberBalanceImpactHistory),
      ),
    );
  }
}

const toMemberBalanceImpactHistory = (
  response: MemberBalanceImpactHistoryResponseDto,
): MemberBalanceImpactHistory => ({
  groupId: response.group,
  member: response.member,
  impacts: response.impacts.map(toMemberBalanceImpact),
});

const toMemberBalanceImpact = (response: MemberBalanceImpactResponseDto): MemberBalanceImpact => ({
  type: toMemberBalanceImpactOperationType(response.type),
  effect: toMemberBalanceImpactType(response.effect),
  operationId: response.operationId,
  amountInCents: response.amountInCents,
  occurredAt: toDate(response.occurredAt),
});

const toMemberBalanceImpactType = (
  effect: MemberBalanceImpactTypeResponseDto | string,
): MemberBalanceImpactType => {
  switch (effect) {
    case 'DEBT_REDUCTION':
    case 'DEBT_INCREASE':
    case 'CREDIT_REDUCTION':
      return effect;
    default:
      throw new Error(`Effet inconnu: ${String(effect)}.`);
  }
};

const toMemberBalanceImpactOperationType = (
  type: MemberBalanceImpactOperationTypeResponseDto | string,
): MemberBalanceImpactOperationType => {
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
