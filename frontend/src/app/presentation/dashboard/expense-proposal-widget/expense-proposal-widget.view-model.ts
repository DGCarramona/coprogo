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
import {
  addCumulativeIntermediateTier,
  CumulativeTiersFormModel,
  emptyCumulativeTiersForm,
  removeCumulativeIntermediateTier,
  setCumulativeIntermediateThreshold,
  toCumulativeTiersAllocation,
  toggleCumulativeFinalParticipant,
  toggleCumulativeIntermediateParticipant,
  validateCumulativeTiersForm,
} from './cumulative-tiers-allocation-form';
import {
  emptyEqualWithCapsForm,
  EqualWithCapsFormModel,
  setEqualWithCapsMaximum,
  toEqualWithCapsAllocation,
  toggleEqualWithCapsParticipant,
  validateEqualWithCapsForm,
} from './equal-with-caps-allocation-form';
import { parseAmountInCents, toggleMember } from './expense-proposal-form';

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

interface ExpenseProposalFormModel {
  title: string;
  amountInEuros: string;
  allocationMode: ExpenseProposalCommand['allocation']['type'];
  equal: {
    participants: readonly string[];
  };
  equalWithCaps: EqualWithCapsFormModel;
  cumulativeTiers: CumulativeTiersFormModel;
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
          value() === 'CUSTOM'
            ? {
                kind: 'mode-unavailable',
                message: 'Les champs de ce mode de repartition ne sont pas encore disponibles.',
              }
            : undefined,
        );
        validate(proposal.equal.participants, ({ value, valueOf }) =>
          valueOf(proposal.allocationMode) === 'EQUAL' && value().length === 0
            ? { kind: 'participants', message: 'Choisissez au moins un participant.' }
            : undefined,
        );
        validate(proposal.equalWithCaps, ({ value, valueOf }) =>
          valueOf(proposal.allocationMode) === 'EQUAL_WITH_CAPS'
            ? validateEqualWithCapsForm(value())
            : undefined,
        );
        validate(proposal.cumulativeTiers, ({ value, valueOf }) => {
          if (valueOf(proposal.allocationMode) !== 'CUMULATIVE_TIERS') return undefined;

          const totalAmountInCents = parseAmountInCents(valueOf(proposal.amountInEuros));
          return totalAmountInCents === null
            ? undefined
            : validateCumulativeTiersForm(value(), totalAmountInCents);
        });
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

            if (proposal.allocationMode === 'CUSTOM') {
              return {
                kind: 'mode-unavailable',
                message: 'Les champs de ce mode de repartition ne sont pas encore disponibles.',
              };
            }

            const equalWithCapsError = validateEqualWithCapsForm(proposal.equalWithCaps);
            if (proposal.allocationMode === 'EQUAL_WITH_CAPS' && equalWithCapsError) {
              return equalWithCapsError;
            }

            const cumulativeTiersError = validateCumulativeTiersForm(
              proposal.cumulativeTiers,
              totalAmountInCents,
            );
            if (proposal.allocationMode === 'CUMULATIVE_TIERS' && cumulativeTiersError) {
              return cumulativeTiersError;
            }

            const allocation: ExpenseAllocation = (() => {
              switch (proposal.allocationMode) {
                case 'EQUAL':
                  return {
                    type: 'EQUAL',
                    participants: new Set(proposal.equal.participants),
                  };
                case 'EQUAL_WITH_CAPS':
                  return toEqualWithCapsAllocation(proposal.equalWithCaps);
                case 'CUMULATIVE_TIERS':
                  return toCumulativeTiersAllocation(proposal.cumulativeTiers, totalAmountInCents);
              }
            })();

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
        participants: toggleMember(proposal.equal.participants, member),
      },
    }));
  }

  toggleEqualWithCapsParticipant(member: string): void {
    this.proposalModel.update((proposal) => ({
      ...proposal,
      equalWithCaps: toggleEqualWithCapsParticipant(proposal.equalWithCaps, member),
    }));
  }

  setEqualWithCapsMaximum(member: string, amountInEuros: string): void {
    this.proposalModel.update((proposal) => {
      const equalWithCaps = setEqualWithCapsMaximum(proposal.equalWithCaps, member, amountInEuros);
      return equalWithCaps === proposal.equalWithCaps ? proposal : { ...proposal, equalWithCaps };
    });
  }

  addCumulativeIntermediateTier(): void {
    this.proposalModel.update((proposal) => ({
      ...proposal,
      cumulativeTiers: addCumulativeIntermediateTier(proposal.cumulativeTiers),
    }));
  }

  removeCumulativeIntermediateTier(index: number): void {
    this.proposalModel.update((proposal) => {
      const cumulativeTiers = removeCumulativeIntermediateTier(proposal.cumulativeTiers, index);
      return cumulativeTiers === proposal.cumulativeTiers
        ? proposal
        : { ...proposal, cumulativeTiers };
    });
  }

  setCumulativeIntermediateThreshold(index: number, upToAmountInEuros: string): void {
    this.proposalModel.update((proposal) => {
      const cumulativeTiers = setCumulativeIntermediateThreshold(
        proposal.cumulativeTiers,
        index,
        upToAmountInEuros,
      );
      return cumulativeTiers === proposal.cumulativeTiers
        ? proposal
        : { ...proposal, cumulativeTiers };
    });
  }

  toggleCumulativeIntermediateParticipant(index: number, member: string): void {
    this.proposalModel.update((proposal) => {
      const cumulativeTiers = toggleCumulativeIntermediateParticipant(
        proposal.cumulativeTiers,
        index,
        member,
      );
      return cumulativeTiers === proposal.cumulativeTiers
        ? proposal
        : { ...proposal, cumulativeTiers };
    });
  }

  toggleCumulativeFinalParticipant(member: string): void {
    this.proposalModel.update((proposal) => ({
      ...proposal,
      cumulativeTiers: toggleCumulativeFinalParticipant(proposal.cumulativeTiers, member),
    }));
  }
}

const emptyProposalFormModel = (): ExpenseProposalFormModel => ({
  title: '',
  amountInEuros: '',
  allocationMode: 'EQUAL',
  equal: {
    participants: [],
  },
  equalWithCaps: emptyEqualWithCapsForm(),
  cumulativeTiers: emptyCumulativeTiersForm(),
});
