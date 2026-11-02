import { computed, Injectable, Injector, signal } from '@angular/core';
import { FieldTree, TreeValidationResult, form, required, validate } from '@angular/forms/signals';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';

import { GroupMembersPort } from '../../application/group/group-members.port';
import {
  OwnershipShareChangePort,
  type OwnershipShareChangeCommand,
} from '../../application/revenue/ownership-share-change.port';
import { describeError } from '../../application/shared/describe-error';
import { parseLocalDate } from '../../shared/format/local-date';
import { groupMembersQueryOptions } from '../shared/group-members/group-members-query';
import { toOwnershipShares } from './ownership-share-change-form';

interface OwnershipShareChangeFormModel {
  effectiveDate: string;
  percentagesByMember: Record<string, string>;
}

@Injectable()
export class OwnershipShareChangeWidgetViewModel {
  private readonly groupIdState = signal<string | null>(null);
  private readonly formModel = signal<OwnershipShareChangeFormModel>(emptyFormModel());
  readonly form: FieldTree<OwnershipShareChangeFormModel>;

  private readonly membersQuery;
  readonly members = computed<readonly string[]>(() => this.membersQuery.data() ?? []);
  readonly isLoading = computed(() => this.membersQuery.isLoading());
  readonly hasLoadError = computed(() => this.membersQuery.isError());
  readonly loadErrorMessage = computed(() => {
    const error = this.membersQuery.error();
    return error === null ? null : describeError(error, 'errors.groups.loadMembers');
  });
  readonly hasInvalidShares = computed(
    () =>
      this.members().length > 0 &&
      toOwnershipShares(this.members(), this.formModel().percentagesByMember) === null,
  );

  private readonly recordingMutation;
  readonly isRecording = computed(() => this.form().submitting());
  readonly isRecorded = computed(() => this.recordingMutation.isSuccess());
  readonly hasRecordingError = computed(() => this.recordingMutation.isError());
  readonly recordingErrorMessage = computed(() => {
    const error = this.recordingMutation.error();
    return error === null ? null : describeError(error, 'errors.ownership.recordShares');
  });

  constructor(
    private readonly groupMembersPort: GroupMembersPort,
    private readonly changePort: OwnershipShareChangePort,
    private readonly queryClient: QueryClient,
    injector: Injector,
  ) {
    this.membersQuery = injectQuery(
      () => groupMembersQueryOptions(this.groupIdState(), this.groupMembersPort),
      { injector },
    );
    this.recordingMutation = injectMutation<void, Error, OwnershipShareChangeCommand>(
      () => ({
        mutationFn: (command) => this.changePort.record(command),
        onSuccess: (_, command) =>
          this.queryClient.invalidateQueries({
            queryKey: ['groups', command.groupId, 'ownership-shares'],
          }),
      }),
      { injector },
    );
    this.form = form(
      this.formModel,
      (shareChange) => {
        required(shareChange.effectiveDate, {
          message: 'validation.ownership.effectiveDateRequired',
        });
        validate(shareChange.effectiveDate, ({ value }) =>
          parseLocalDate(value()) !== null
            ? undefined
            : { kind: 'effective-date', message: 'validation.ownership.effectiveDateInvalid' },
        );
        validate(shareChange.percentagesByMember, ({ value }) =>
          toOwnershipShares(this.members(), value()) === null
            ? {
                kind: 'ownership-shares',
                message: 'validation.ownership.totalMustEqualHundred',
              }
            : undefined,
        );
      },
      {
        injector,
        submission: {
          action: async (submittedForm): Promise<TreeValidationResult> => {
            const value = submittedForm().value();
            const shares = toOwnershipShares(this.members(), value.percentagesByMember);
            const groupId = this.groupIdState();
            const effectiveDate = parseLocalDate(value.effectiveDate);
            if (groupId === null || effectiveDate === null || shares === null) {
              return {
                kind: 'ownership-share-change',
                message: 'validation.ownership.completeForm',
              };
            }

            try {
              await this.recordingMutation.mutateAsync({
                groupId,
                effectiveDate,
                shares,
              });
              submittedForm().reset(emptyFormModel());
              return undefined;
            } catch {
              return {
                kind: 'ownership-share-change',
                message: 'errors.ownership.recordShares',
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

  setPercentage(member: string, percentage: string): void {
    this.formModel.update((model) => ({
      ...model,
      percentagesByMember: {
        ...model.percentagesByMember,
        [member]: percentage,
      },
    }));
  }

  percentageFor(member: string): string {
    return this.formModel().percentagesByMember[member] ?? '';
  }

  retry(): void {
    void this.membersQuery.refetch();
  }
}

const emptyFormModel = (): OwnershipShareChangeFormModel => ({
  effectiveDate: '',
  percentagesByMember: {},
});
