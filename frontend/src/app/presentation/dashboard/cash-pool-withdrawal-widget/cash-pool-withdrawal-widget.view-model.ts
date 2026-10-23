import { computed, Injectable, Injector, signal } from '@angular/core';
import { FieldTree, TreeValidationResult, form, required, validate } from '@angular/forms/signals';
import { injectMutation, QueryClient } from '@tanstack/angular-query-experimental';

import {
  CashPoolWithdrawalPort,
  type CashPoolWithdrawalCommand,
} from '../../../application/ledger/cash-pool-withdrawal.port';
import { describeError } from '../../../application/shared/describe-error';
import { parseAmountInCents } from '../../../shared/format/parse-amount-in-cents';
import { parseLocalDateTime } from '../../../shared/format/parse-local-date-time';
import { cashPoolHistoryQueryKey } from '../cash-pool-query-keys';

interface CashPoolWithdrawalFormModel {
  amountInEuros: string;
  withdrawnAt: string;
}

@Injectable()
export class CashPoolWithdrawalWidgetViewModel {
  private readonly groupIdState = signal<string | null>(null);
  private readonly formModel = signal<CashPoolWithdrawalFormModel>(emptyFormModel());
  readonly form: FieldTree<CashPoolWithdrawalFormModel>;

  private readonly withdrawalMutation;
  readonly isWithdrawing = computed(() => this.form().submitting());
  readonly isWithdrawn = computed(() => this.withdrawalMutation.isSuccess());
  readonly hasWithdrawalError = computed(() => this.withdrawalMutation.isError());
  readonly withdrawalErrorMessage = computed(() => {
    const error = this.withdrawalMutation.error();
    return error === null ? null : describeError(error, 'Le retrait n a pas pu etre enregistre.');
  });

  constructor(
    withdrawalPort: CashPoolWithdrawalPort,
    private readonly queryClient: QueryClient,
    injector: Injector,
  ) {
    this.withdrawalMutation = injectMutation<void, Error, CashPoolWithdrawalCommand>(
      () => ({ mutationFn: (command) => withdrawalPort.withdraw(command) }),
      { injector },
    );
    this.form = form(
      this.formModel,
      (withdrawal) => {
        validate(withdrawal.amountInEuros, ({ value }) =>
          parseAmountInCents(value()) === null
            ? {
                kind: 'amount',
                message: 'Indiquez un montant positif avec deux decimales au plus.',
              }
            : undefined,
        );
        required(withdrawal.withdrawnAt, { message: 'Indiquez la date du retrait.' });
        validate(withdrawal.withdrawnAt, ({ value }) =>
          parseLocalDateTime(value()) === null
            ? { kind: 'withdrawn-at', message: 'Indiquez une date de retrait valide.' }
            : undefined,
        );
      },
      {
        injector,
        submission: {
          action: async (submittedForm): Promise<TreeValidationResult> => {
            const value = submittedForm().value();
            const amountInCents = parseAmountInCents(value.amountInEuros);
            const withdrawnAt = parseLocalDateTime(value.withdrawnAt);
            const groupId = this.groupIdState();
            if (groupId === null || amountInCents === null || withdrawnAt === null) {
              return {
                kind: 'cash-pool-withdrawal',
                message: 'Completez le montant et la date du retrait.',
              };
            }

            try {
              await this.withdrawalMutation.mutateAsync({ groupId, amountInCents, withdrawnAt });
              void this.queryClient.invalidateQueries({
                queryKey: cashPoolHistoryQueryKey(groupId),
              });
              submittedForm().reset(emptyFormModel());
              return undefined;
            } catch {
              return {
                kind: 'cash-pool-withdrawal',
                message: 'Le retrait n a pas pu etre enregistre.',
              };
            }
          },
        },
      },
    );
  }

  initialize(groupId: string): void {
    this.groupIdState.set(groupId);
    this.form().reset(emptyFormModel());
  }
}

const emptyFormModel = (): CashPoolWithdrawalFormModel => ({
  amountInEuros: '',
  withdrawnAt: '',
});
