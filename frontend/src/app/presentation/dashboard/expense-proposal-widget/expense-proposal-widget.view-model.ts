import { computed, Injectable, Injector, signal } from '@angular/core';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { firstValueFrom } from 'rxjs';

import {
  ExpenseProposalPort,
  ProposeEqualSplitExpenseCommand,
} from '../../../application/expense/expense-proposal.port';
import { GroupMembersPort } from '../../../application/group/group-members.port';
import { describeError } from '../../../application/shared/describe-error';
import { GroupMember } from '../../../domain/group/group-member';

export interface EqualSplitExpenseProposalInput {
  title: string;
  totalAmountInCents: number;
  participants: ReadonlySet<string>;
}

@Injectable()
export class ExpenseProposalWidgetViewModel {
  private readonly groupIdState = signal<string | null>(null);

  private readonly membersQuery;
  readonly members = computed<readonly GroupMember[]>(() => this.membersQuery.data() ?? []);
  readonly isLoading = computed(() => this.membersQuery.isLoading());
  readonly hasLoadError = computed(() => this.membersQuery.isError());
  readonly errorMessage = computed(() => {
    const error = this.membersQuery.error();

    return error === null
      ? null
      : describeError(error, 'Les membres du groupe n ont pas pu etre charges.');
  });

  private readonly proposalMutation;
  readonly isProposing = computed(() => this.proposalMutation.isPending());
  readonly isProposed = computed(() => this.proposalMutation.isSuccess());
  readonly hasProposalError = computed(() => this.proposalMutation.isError());
  readonly proposalErrorMessage = computed(() => {
    const error = this.proposalMutation.error();

    return error === null ? null : describeError(error, 'La depense n a pas pu etre proposee.');
  });

  constructor(
    private readonly groupMembersPort: GroupMembersPort,
    private readonly expenseProposalPort: ExpenseProposalPort,
    private readonly queryClient: QueryClient,
    injector: Injector,
  ) {
    this.membersQuery = injectQuery(
      () => {
        const groupId = this.groupIdState();

        return {
          queryKey: ['groups', groupId, 'members'] as const,
          queryFn: () => {
            if (groupId === null) {
              throw new Error('Un groupe doit etre initialise avant de charger ses membres.');
            }

            return firstValueFrom(this.groupMembersPort.listByGroup(groupId));
          },
          enabled: groupId !== null,
          staleTime: 30_000,
        };
      },
      { injector },
    );
    this.proposalMutation = injectMutation<void, Error, ProposeEqualSplitExpenseCommand>(
      () => ({
        mutationFn: (command) => this.expenseProposalPort.proposeEqualSplit(command),
        onSuccess: (_, command) =>
          this.queryClient.invalidateQueries({
            queryKey: ['groups', command.groupId, 'expenses'],
          }),
      }),
      { injector },
    );
  }

  initialize(groupId: string): void {
    this.groupIdState.set(groupId);
  }

  retry(): void {
    void this.membersQuery.refetch();
  }

  proposeEqualSplit(input: EqualSplitExpenseProposalInput): void {
    const groupId = this.groupIdState();
    if (groupId === null) {
      throw new Error('Un groupe doit etre initialise avant de proposer une depense.');
    }

    this.proposalMutation.mutate({ ...input, groupId });
  }
}
