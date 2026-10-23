import { computed, Injectable, Injector, signal } from '@angular/core';
import { FieldTree, TreeValidationResult, form, required, validate } from '@angular/forms/signals';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';

import { GroupMembersPort } from '../../../application/group/group-members.port';
import {
  DirectReimbursementRecordingPort,
  type DirectReimbursementRecordingCommand,
} from '../../../application/reimbursement/direct-reimbursement-recording.port';
import { describeError } from '../../../application/shared/describe-error';
import { parseAmountInCents } from '../../../shared/format/parse-amount-in-cents';
import { parseLocalDateTime } from '../../../shared/format/parse-local-date-time';
import { groupMembersQueryOptions } from '../../shared/group-members/group-members-query';

interface DirectReimbursementFormModel {
  paidBy: string;
  amountInEuros: string;
  reimbursedAt: string;
}

@Injectable()
export class DirectReimbursementWidgetViewModel {
  private readonly groupIdState = signal<string | null>(null);
  private readonly formModel = signal<DirectReimbursementFormModel>(emptyFormModel());
  readonly form: FieldTree<DirectReimbursementFormModel>;

  private readonly membersQuery;
  readonly members = computed<readonly string[]>(() => this.membersQuery.data() ?? []);
  readonly isLoading = computed(() => this.membersQuery.isLoading());
  readonly hasLoadError = computed(() => this.membersQuery.isError());
  readonly loadErrorMessage = computed(() => {
    const error = this.membersQuery.error();
    return error === null
      ? null
      : describeError(error, 'Les membres du groupe n ont pas pu etre charges.');
  });

  private readonly recordingMutation;
  readonly isRecording = computed(() => this.form().submitting());
  readonly isRecorded = computed(() => this.recordingMutation.isSuccess());
  readonly hasRecordingError = computed(() => this.recordingMutation.isError());
  readonly recordingErrorMessage = computed(() => {
    const error = this.recordingMutation.error();
    return error === null
      ? null
      : describeError(error, 'Le remboursement n a pas pu etre enregistre.');
  });

  constructor(
    private readonly groupMembersPort: GroupMembersPort,
    private readonly recordingPort: DirectReimbursementRecordingPort,
    private readonly queryClient: QueryClient,
    injector: Injector,
  ) {
    this.membersQuery = injectQuery(
      () => groupMembersQueryOptions(this.groupIdState(), this.groupMembersPort),
      { injector },
    );
    this.recordingMutation = injectMutation<void, Error, DirectReimbursementRecordingCommand>(
      () => ({
        mutationFn: (command) => this.recordingPort.record(command),
        onSuccess: (_, command) =>
          this.queryClient.invalidateQueries({
            queryKey: ['groups', command.groupId, 'reimbursements'],
          }),
      }),
      { injector },
    );
    this.form = form(
      this.formModel,
      (reimbursement) => {
        required(reimbursement.paidBy, { message: 'Choisissez la personne qui vous a rembourse.' });
        validate(reimbursement.amountInEuros, ({ value }) =>
          parseAmountInCents(value()) === null
            ? {
                kind: 'amount',
                message: 'Indiquez un montant positif avec deux decimales au plus.',
              }
            : undefined,
        );
        required(reimbursement.reimbursedAt, { message: 'Indiquez la date du remboursement.' });
        validate(reimbursement.reimbursedAt, ({ value }) =>
          parseLocalDateTime(value()) === null
            ? { kind: 'date', message: 'Indiquez une date de remboursement valide.' }
            : undefined,
        );
      },
      {
        injector,
        submission: {
          action: async (submittedForm): Promise<TreeValidationResult> => {
            const value = submittedForm().value();
            const amountInCents = parseAmountInCents(value.amountInEuros);
            const reimbursedAt = parseLocalDateTime(value.reimbursedAt);
            const groupId = this.groupIdState();

            if (groupId === null || amountInCents === null || reimbursedAt === null) {
              return {
                kind: 'direct-reimbursement',
                message: 'Completez les informations du remboursement.',
              };
            }

            try {
              await this.recordingMutation.mutateAsync({
                groupId,
                paidBy: value.paidBy,
                amountInCents,
                reimbursedAt,
              });
              submittedForm().reset(emptyFormModel());
              return undefined;
            } catch {
              return {
                kind: 'direct-reimbursement',
                message: 'Le remboursement n a pas pu etre enregistre.',
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

  retry(): void {
    void this.membersQuery.refetch();
  }
}

const emptyFormModel = (): DirectReimbursementFormModel => ({
  paidBy: '',
  amountInEuros: '',
  reimbursedAt: '',
});
