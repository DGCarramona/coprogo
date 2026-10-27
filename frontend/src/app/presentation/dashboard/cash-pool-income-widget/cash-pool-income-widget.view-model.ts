import { computed, Injectable, Injector, signal } from '@angular/core';
import { FieldTree, TreeValidationResult, form, required, validate } from '@angular/forms/signals';
import { injectMutation, QueryClient } from '@tanstack/angular-query-experimental';

import {
  CashPoolIncomeRecordingPort,
  type CashPoolIncomeRecordingCommand,
} from '../../../application/revenue/cash-pool-income-recording.port';
import { describeError } from '../../../application/shared/describe-error';
import { parseAmountInCents } from '../../../shared/format/parse-amount-in-cents';
import { formatLocalDate, parseLocalDate } from '../../../shared/format/local-date';
import { parseLocalDateTime } from '../../../shared/format/parse-local-date-time';
import { cashPoolHistoryQueryKey } from '../cash-pool-query-keys';

interface CashPoolIncomeFormModel {
  amountInEuros: string;
  receivedAt: string;
  effectiveDate: string;
}

@Injectable()
export class CashPoolIncomeWidgetViewModel {
  private readonly groupIdState = signal<string | null>(null);
  private readonly formModel = signal<CashPoolIncomeFormModel>(emptyFormModel());
  readonly form: FieldTree<CashPoolIncomeFormModel>;

  private readonly recordingMutation;
  readonly isRecording = computed(() => this.form().submitting());
  readonly isRecorded = computed(() => this.recordingMutation.isSuccess());
  readonly hasRecordingError = computed(() => this.recordingMutation.isError());
  readonly recordingErrorMessage = computed(() => {
    const error = this.recordingMutation.error();
    return error === null ? null : describeError(error, 'Le revenu n a pas pu etre enregistre.');
  });

  constructor(
    recordingPort: CashPoolIncomeRecordingPort,
    private readonly queryClient: QueryClient,
    injector: Injector,
  ) {
    this.recordingMutation = injectMutation<void, Error, CashPoolIncomeRecordingCommand>(
      () => ({ mutationFn: (command) => recordingPort.record(command) }),
      { injector },
    );
    this.form = form(
      this.formModel,
      (income) => {
        validate(income.amountInEuros, ({ value }) =>
          parseAmountInCents(value()) === null
            ? {
                kind: 'amount',
                message: 'Indiquez un montant positif avec deux decimales au plus.',
              }
            : undefined,
        );
        required(income.receivedAt, { message: 'Indiquez la date de reception.' });
        validate(income.receivedAt, ({ value }) =>
          parseLocalDateTime(value()) === null
            ? { kind: 'received-at', message: 'Indiquez une date de reception valide.' }
            : undefined,
        );
        required(income.effectiveDate, { message: 'Indiquez la date des quotes-parts.' });
        validate(income.effectiveDate, ({ value }) =>
          parseLocalDate(value()) === null
            ? { kind: 'effective-date', message: 'Indiquez une date de quotes-parts valide.' }
            : undefined,
        );
      },
      {
        injector,
        submission: {
          action: async (submittedForm): Promise<TreeValidationResult> => {
            const value = submittedForm().value();
            const amountInCents = parseAmountInCents(value.amountInEuros);
            const receivedAt = parseLocalDateTime(value.receivedAt);
            const effectiveDate = parseLocalDate(value.effectiveDate);
            const groupId = this.groupIdState();
            if (
              groupId === null ||
              amountInCents === null ||
              receivedAt === null ||
              effectiveDate === null
            ) {
              return {
                kind: 'cash-pool-income',
                message: 'Completez le montant et les dates du revenu.',
              };
            }

            try {
              await this.recordingMutation.mutateAsync({
                groupId,
                amountInCents,
                receivedAt,
                effectiveDate,
              });
              void this.queryClient.invalidateQueries({
                queryKey: cashPoolHistoryQueryKey(groupId),
              });
              submittedForm().reset(emptyFormModel());
              return undefined;
            } catch {
              return {
                kind: 'cash-pool-income',
                message: 'Le revenu n a pas pu etre enregistre.',
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

const emptyFormModel = (): CashPoolIncomeFormModel => ({
  amountInEuros: '',
  receivedAt: '',
  effectiveDate: formatLocalDate(new Date()),
});
