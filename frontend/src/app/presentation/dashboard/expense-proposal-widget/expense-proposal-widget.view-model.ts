import { computed, Injectable, Injector, signal } from '@angular/core';
import { FieldTree, TreeValidationResult, form, required, validate } from '@angular/forms/signals';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { firstValueFrom } from 'rxjs';

import {
  ExpenseProposalPort,
  ExpenseProposalCommand,
  ExpenseAllocation,
} from '../../../application/expense/expense-proposal.port';
import { GroupMembersPort } from '../../../application/group/group-members.port';
import { describeError } from '../../../application/shared/describe-error';
import { GroupMember } from '../../../domain/group/group-member';

export interface EqualSplitExpenseProposalInput {
  title: string;
  totalAmountInCents: number;
  participants: ReadonlySet<string>;
}

interface ExpenseProposalInput {
  title: string;
  totalAmountInCents: number;
  allocation: ExpenseAllocation;
}

interface EqualWithCapsFormModel {
  participants: readonly string[];
  maximumAmountsInEuros: Readonly<Partial<Record<string, string>>>;
}

interface ExpenseProposalFormModel {
  title: string;
  amountInEuros: string;
  allocationMode: ExpenseProposalCommand['allocation']['type'];
  equal: {
    participants: readonly string[];
  };
  equalWithCaps: EqualWithCapsFormModel;
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
        validate(proposal.allocationMode, ({ value }) =>
          value() === 'EQUAL' || value() === 'EQUAL_WITH_CAPS'
            ? undefined
            : {
                kind: 'mode-unavailable',
                message: 'Les champs de ce mode de repartition ne sont pas encore disponibles.',
              },
        );
        validate(proposal.equal.participants, ({ value, valueOf }) =>
          valueOf(proposal.allocationMode) === 'EQUAL' && value().length === 0
            ? { kind: 'participants', message: 'Choisissez au moins un participant.' }
            : undefined,
        );
        validate(proposal.equalWithCaps, ({ value, valueOf }) =>
          valueOf(proposal.allocationMode) === 'EQUAL_WITH_CAPS'
            ? equalWithCapsValidationError(value())
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

            if (
              proposal.allocationMode === 'CUMULATIVE_TIERS' ||
              proposal.allocationMode === 'CUSTOM'
            ) {
              return {
                kind: 'mode-unavailable',
                message: 'Les champs de ce mode de repartition ne sont pas encore disponibles.',
              };
            }

            const equalWithCapsError = equalWithCapsValidationError(proposal.equalWithCaps);
            if (proposal.allocationMode === 'EQUAL_WITH_CAPS' && equalWithCapsError) {
              return equalWithCapsError;
            }

            const allocation: ExpenseAllocation =
              proposal.allocationMode === 'EQUAL'
                ? {
                    type: 'EQUAL',
                    participants: new Set(proposal.equal.participants),
                  }
                : {
                    type: 'EQUAL_WITH_CAPS',
                    participants: new Set(proposal.equalWithCaps.participants),
                    capsInCentsByMember: toCapsInCentsByMember(proposal.equalWithCaps),
                  };

            try {
              await this.proposalMutation.mutateAsync(
                this.commandFor({
                  title: proposal.title.trim(),
                  totalAmountInCents,
                  allocation,
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
    this.proposalMutation.mutate(
      this.commandFor({
        title: input.title,
        totalAmountInCents: input.totalAmountInCents,
        allocation: {
          type: 'EQUAL',
          participants: input.participants,
        },
      }),
    );
  }

  private commandFor(input: ExpenseProposalInput): ExpenseProposalCommand {
    const groupId = this.groupIdState();
    if (groupId === null) {
      throw new Error('Un groupe doit etre initialise avant de proposer une depense.');
    }

    return {
      groupId,
      title: input.title,
      totalAmountInCents: input.totalAmountInCents,
      allocation: input.allocation,
    };
  }

  toggleParticipant(member: string): void {
    this.proposalModel.update((proposal) => ({
      ...proposal,
      equal: {
        participants: proposal.equal.participants.includes(member)
          ? proposal.equal.participants.filter((participant) => participant !== member)
          : [...proposal.equal.participants, member],
      },
    }));
  }

  toggleEqualWithCapsParticipant(member: string): void {
    this.proposalModel.update((proposal) => {
      const isSelected = proposal.equalWithCaps.participants.includes(member);

      return {
        ...proposal,
        equalWithCaps: {
          participants: isSelected
            ? proposal.equalWithCaps.participants.filter((participant) => participant !== member)
            : [...proposal.equalWithCaps.participants, member],
          maximumAmountsInEuros: isSelected
            ? withoutMember(proposal.equalWithCaps.maximumAmountsInEuros, member)
            : proposal.equalWithCaps.maximumAmountsInEuros,
        },
      };
    });
  }

  setEqualWithCapsMaximum(member: string, amountInEuros: string): void {
    this.proposalModel.update((proposal) => {
      if (!proposal.equalWithCaps.participants.includes(member)) return proposal;

      return {
        ...proposal,
        equalWithCaps: {
          ...proposal.equalWithCaps,
          maximumAmountsInEuros:
            amountInEuros.trim().length === 0
              ? withoutMember(proposal.equalWithCaps.maximumAmountsInEuros, member)
              : {
                  ...proposal.equalWithCaps.maximumAmountsInEuros,
                  [member]: amountInEuros,
                },
        },
      };
    });
  }
}

const emptyProposalFormModel = (): ExpenseProposalFormModel => ({
  title: '',
  amountInEuros: '',
  allocationMode: 'EQUAL',
  equal: {
    participants: [],
  },
  equalWithCaps: {
    participants: [],
    maximumAmountsInEuros: {},
  },
});

const equalWithCapsValidationError = (
  equalWithCaps: EqualWithCapsFormModel,
): { kind: string; message: string } | undefined => {
  if (equalWithCaps.participants.length === 0) {
    return { kind: 'participants', message: 'Choisissez au moins un participant.' };
  }

  const hasInvalidMaximum = equalWithCaps.participants.some((member) => {
    const maximum = equalWithCaps.maximumAmountsInEuros[member]?.trim() ?? '';
    return maximum.length > 0 && parseAmountInCents(maximum) === null;
  });
  if (hasInvalidMaximum) {
    return {
      kind: 'maximum-amount',
      message: 'Indiquez un montant maximum positif avec deux decimales au plus.',
    };
  }

  const hasUncappedParticipant = equalWithCaps.participants.some(
    (member) => (equalWithCaps.maximumAmountsInEuros[member]?.trim() ?? '').length === 0,
  );
  return hasUncappedParticipant
    ? undefined
    : {
        kind: 'uncapped-participant',
        message: 'Laissez au moins un participant sans montant maximum.',
      };
};

const toCapsInCentsByMember = (
  equalWithCaps: EqualWithCapsFormModel,
): ReadonlyMap<string, number> =>
  new Map(
    equalWithCaps.participants.flatMap((member): [string, number][] => {
      const maximum = equalWithCaps.maximumAmountsInEuros[member]?.trim() ?? '';
      if (maximum.length === 0) return [];

      const maximumInCents = parseAmountInCents(maximum);
      return maximumInCents === null ? [] : [[member, maximumInCents]];
    }),
  );

const withoutMember = (
  maximumAmountsInEuros: Readonly<Partial<Record<string, string>>>,
  member: string,
): Readonly<Partial<Record<string, string>>> =>
  Object.fromEntries(
    Object.entries(maximumAmountsInEuros).filter(([cappedMember]) => cappedMember !== member),
  );

const parseAmountInCents = (amount: string): number | null => {
  const match = /^(\d+)(?:[,.](\d{1,2}))?$/.exec(amount.trim());
  if (!match) return null;
  const whole = Number(match[1]);
  const fraction = Number((match[2] ?? '').padEnd(2, '0'));
  const cents = whole * 100 + fraction;
  return Number.isSafeInteger(cents) && cents > 0 ? cents : null;
};
