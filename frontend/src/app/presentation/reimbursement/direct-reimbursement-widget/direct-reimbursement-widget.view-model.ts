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
    return error === null ? null : describeError(error, 'errors.groups.loadMembers');
  });

  private readonly recordingMutation;
  readonly isRecording = computed(() => this.form().submitting());
  readonly isRecorded = computed(() => this.recordingMutation.isSuccess());
  readonly hasRecordingError = computed(() => this.recordingMutation.isError());
  readonly recordingErrorMessage = computed(() => {
    const error = this.recordingMutation.error();
    return error === null ? null : describeError(error, 'errors.reimbursements.record');
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
        required(reimbursement.paidBy, { message: 'validation.reimbursements.payerRequired' });
        validate(reimbursement.amountInEuros, ({ value }) =>
          parseAmountInCents(value()) === null
            ? {
                kind: 'amount',
                message: 'validation.amount.positiveTwoDecimals',
              }
            : undefined,
        );
        required(reimbursement.reimbursedAt, { message: 'validation.reimbursements.dateRequired' });
        validate(reimbursement.reimbursedAt, ({ value }) =>
          parseLocalDateTime(value()) === null
            ? { kind: 'date', message: 'validation.reimbursements.dateInvalid' }
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
                message: 'validation.reimbursements.completeReceived',
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
                message: 'errors.reimbursements.record',
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
