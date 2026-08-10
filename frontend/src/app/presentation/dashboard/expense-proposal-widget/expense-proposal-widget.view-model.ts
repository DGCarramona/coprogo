import { computed, Injectable, Injector, signal } from '@angular/core';
import { FieldTree, TreeValidationResult, form, required, validate } from '@angular/forms/signals';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { firstValueFrom } from 'rxjs';

import {
  ExpenseProposalPort,
  ExpenseProposalCommand,
} from '../../../application/expense/expense-proposal.port';
import { GroupMembersPort } from '../../../application/group/group-members.port';
import { describeError } from '../../../application/shared/describe-error';
import { GroupMember } from '../../../domain/group/group-member';

export interface EqualSplitExpenseProposalInput {
  title: string;
  totalAmountInCents: number;
  participants: ReadonlySet<string>;
}

interface ExpenseProposalFormModel {
  title: string;
  amountInEuros: string;
  participants: readonly string[];
}

@Injectable()
export class ExpenseProposalWidgetViewModel {
  private readonly groupIdState = signal<string | null>(null);
  private readonly proposalModel = signal<ExpenseProposalFormModel>(emptyProposalFormModel());
  readonly proposalForm: FieldTree<ExpenseProposalFormModel>;

  private readonly membersQuery;
  readonly members = computed<readonly string[]>(() => this.membersQuery.data() ?? []);
  readonly isLoading = computed(() => this.membersQuery.isLoading());
  readonly hasLoadError = computed(() => this.membersQuery.isError());
  readonly errorMessage = computed(() => {
    const error = this.membersQuery.error();

    return error === null
      ? null
      : describeError(error, 'Les membres du groupe n ont pas pu etre charges.');
  });

  private readonly proposalMutation;
  readonly isProposing = computed(() => this.proposalForm().submitting());
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
          queryFn: (): Promise<readonly GroupMember[]> => {
            if (groupId === null) {
              throw new Error('Un groupe doit etre initialise avant de charger ses membres.');
            }

            return firstValueFrom(this.groupMembersPort.listByGroup(groupId));
          },
          select: (members) => members.map((member) => member.member),
          enabled: groupId !== null,
          staleTime: 30_000,
        };
      },
      { injector },
    );
    this.proposalMutation = injectMutation<void, Error, ExpenseProposalCommand>(
      () => ({
        mutationFn: (command) => this.expenseProposalPort.propose(command),
        onSuccess: (_, command) =>
          this.queryClient.invalidateQueries({
            queryKey: ['groups', command.groupId, 'expenses'],
          }),
      }),
      { injector },
    );
    this.proposalForm = form(
      this.proposalModel,
      (proposal) => {
        required(proposal.title, { message: 'Le titre est obligatoire.' });
        validate(proposal.title, ({ value }) =>
          value().trim().length > 0
            ? undefined
            : { kind: 'blank-title', message: 'Le titre ne peut pas etre vide.' },
        );
        validate(proposal.amountInEuros, ({ value }) =>
          parseAmountInCents(value()) === null
            ? {
                kind: 'amount',
                message: 'Indiquez un montant positif avec deux decimales au plus.',
              }
            : undefined,
        );
        validate(proposal.participants, ({ value }) =>
          value().length === 0
            ? { kind: 'participants', message: 'Choisissez au moins un participant.' }
            : undefined,
        );
      },
      {
        injector,
        submission: {
          action: async (form): Promise<TreeValidationResult> => {
            const proposal = form().value();
            const totalAmountInCents = parseAmountInCents(proposal.amountInEuros);

            if (totalAmountInCents === null) {
              return {
                kind: 'amount',
                message: 'Indiquez un montant positif avec deux decimales au plus.',
              };
            }

            try {
              await this.proposalMutation.mutateAsync(
                this.commandFor({
                  title: proposal.title.trim(),
                  totalAmountInCents,
                  participants: new Set(proposal.participants),
                }),
              );

              return undefined;
            } catch {
              return {
                kind: 'proposal',
                message: 'La depense n a pas pu etre proposee.',
              };
            }
          },
        },
      },
    );
  }

  initialize(groupId: string): void {
    this.groupIdState.set(groupId);
    this.proposalForm().reset(emptyProposalFormModel());
  }

  retry(): void {
    void this.membersQuery.refetch();
  }

  proposeEqualSplit(input: EqualSplitExpenseProposalInput): void {
    this.proposalMutation.mutate(this.commandFor(input));
  }

  private commandFor(input: EqualSplitExpenseProposalInput): ExpenseProposalCommand {
    const groupId = this.groupIdState();
    if (groupId === null) {
      throw new Error('Un groupe doit etre initialise avant de proposer une depense.');
    }

    return {
      groupId,
      title: input.title,
      totalAmountInCents: input.totalAmountInCents,
      allocation: {
        type: 'EQUAL',
        participants: input.participants,
      },
    };
  }

  toggleParticipant(member: string): void {
    this.proposalModel.update((proposal) => ({
      ...proposal,
      participants: proposal.participants.includes(member)
        ? proposal.participants.filter((participant) => participant !== member)
        : [...proposal.participants, member],
    }));
  }
}

const emptyProposalFormModel = (): ExpenseProposalFormModel => ({
  title: '',
  amountInEuros: '',
  participants: [],
});

const parseAmountInCents = (amount: string): number | null => {
  const match = /^(\d+)(?:[,.](\d{1,2}))?$/.exec(amount.trim());
  if (!match) return null;
  const whole = Number(match[1]);
  const fraction = Number((match[2] ?? '').padEnd(2, '0'));
  const cents = whole * 100 + fraction;
  return Number.isSafeInteger(cents) && cents > 0 ? cents : null;
};
