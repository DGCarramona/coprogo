import { Injector } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { submit } from '@angular/forms/signals';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { Observable, of, throwError } from 'rxjs';

import { GroupMembersPort } from '../../../application/group/group-members.port';
import {
  ExpenseProposalPort,
  ExpenseProposalCommand,
} from '../../../application/expense/expense-proposal.port';
import { GroupMember } from '../../../domain/group/group-member';
import { ExpenseProposalWidgetViewModel } from './expense-proposal-widget.view-model';

describe('ExpenseProposalWidgetViewModel', () => {
  let queryClient: QueryClient;
  let port: StubGroupMembersPort;
  let proposalPort: StubExpenseProposalPort;
  let injector: Injector;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
      },
    });
    TestBed.configureTestingModule({
      providers: [provideTanStackQuery(queryClient)],
    });
    port = new StubGroupMembersPort();
    proposalPort = new StubExpenseProposalPort();
    injector = TestBed.inject(Injector);
  });

  it('does not request members before initialization', () => {
    createViewModel();

    expect(port.requestedGroupIds).toEqual([]);
  });

  it('loads members and stores them under the scoped query key', async () => {
    const viewModel = createViewModel();

    viewModel.initialize('group-1');

    await waitFor(() => viewModel.members().length === 2);

    expect(viewModel.members()).toEqual(['alice@example.com', 'bob@example.com']);
    expect(queryClient.getQueryData(['groups', 'group-1', 'members'])).toEqual(port.members);
  });

  it('reuses fresh members for the same group across view model instances', async () => {
    const firstViewModel = createViewModel();
    firstViewModel.initialize('group-1');
    await waitFor(() => firstViewModel.members().length === 2);

    const secondViewModel = createViewModel();
    secondViewModel.initialize('group-1');
    await waitFor(() => secondViewModel.members().length === 2);

    expect(port.requestedGroupIds).toEqual(['group-1']);
  });

  it('exposes a member loading error', async () => {
    port.failure = new Error('Membres indisponibles');
    const viewModel = createViewModel();

    viewModel.initialize('group-1');

    await waitFor(() => viewModel.hasLoadError());

    expect(viewModel.errorMessage()).toBe('Membres indisponibles');
  });

  it('retries after a member loading error', async () => {
    port.failure = new Error('Membres indisponibles');
    const viewModel = createViewModel();
    viewModel.initialize('group-1');
    await waitFor(() => viewModel.hasLoadError());

    port.failure = null;
    viewModel.retry();

    await waitFor(() => viewModel.members().length === 2);

    expect(port.requestedGroupIds).toEqual(['group-1', 'group-1']);
  });

  it('sends a complete equal split proposal for the initialized group', async () => {
    const viewModel = createViewModel();
    viewModel.initialize('group-1');

    viewModel.proposeEqualSplit({
      title: 'Reparation toiture',
      totalAmountInCents: 12500,
      participants: new Set(['alice@example.com', 'bob@example.com']),
    });

    await waitFor(() => proposalPort.commands.length === 1);

    expect(proposalPort.commands).toEqual([
      {
        groupId: 'group-1',
        title: 'Reparation toiture',
        totalAmountInCents: 12500,
        allocation: {
          type: 'EQUAL',
          participants: new Set(['alice@example.com', 'bob@example.com']),
        },
      },
    ]);
  });

  it('exposes Signal Form submission while the proposal is pending', async () => {
    proposalPort.useDeferredResult();
    const viewModel = createViewModel();
    viewModel.initialize('group-1');
    viewModel.proposalForm.title().value.set('Reparation toiture');
    viewModel.proposalForm.amountInEuros().value.set('125');
    viewModel.toggleParticipant('alice@example.com');

    const submission = submit(viewModel.proposalForm);

    await waitFor(() => proposalPort.commands.length === 1);
    await waitFor(() => viewModel.isProposing());

    proposalPort.resolveDeferred();
    await expect(submission).resolves.toBe(true);
    await waitFor(() => viewModel.isProposed());
  });

  it('invalidates only the group expenses query after a proposal succeeds', async () => {
    queryClient.setQueryData(['groups', 'group-1', 'expenses'], ['cached-expense']);
    queryClient.setQueryData(['groups', 'group-1', 'members'], ['cached-member']);
    const viewModel = createViewModel();
    viewModel.initialize('group-1');

    viewModel.proposeEqualSplit({
      title: 'Reparation toiture',
      totalAmountInCents: 12500,
      participants: new Set(),
    });

    await waitFor(() => proposalPort.commands.length === 1);
    await waitFor(() => viewModel.isProposed());

    expect(queryClient.getQueryState(['groups', 'group-1', 'expenses'])?.isInvalidated).toBe(true);
    expect(queryClient.getQueryState(['groups', 'group-1', 'members'])?.isInvalidated).toBe(false);
  });

  it('exposes a proposal error without invalidating expenses', async () => {
    proposalPort.failure = new Error('Proposition indisponible');
    queryClient.setQueryData(['groups', 'group-1', 'expenses'], ['cached-expense']);
    const viewModel = createViewModel();
    viewModel.initialize('group-1');

    viewModel.proposeEqualSplit({
      title: 'Reparation toiture',
      totalAmountInCents: 12500,
      participants: new Set(),
    });

    await waitFor(() => proposalPort.commands.length === 1);
    await waitFor(() => viewModel.hasProposalError());

    expect(viewModel.proposalErrorMessage()).toBe('Proposition indisponible');
    expect(queryClient.getQueryState(['groups', 'group-1', 'expenses'])?.isInvalidated).toBe(false);
  });

  it('reports a failed Signal Form submission while exposing the proposal error', async () => {
    proposalPort.failure = new Error('Proposition indisponible');
    const viewModel = createViewModel();
    viewModel.initialize('group-1');
    viewModel.proposalForm.title().value.set('Reparation toiture');
    viewModel.proposalForm.amountInEuros().value.set('125');
    viewModel.toggleParticipant('alice@example.com');

    await expect(submit(viewModel.proposalForm)).resolves.toBe(false);
    await waitFor(() => viewModel.hasProposalError());

    expect(viewModel.proposalErrorMessage()).toBe('Proposition indisponible');
  });

  it('fails fast without calling the proposal port before initialization', () => {
    const viewModel = createViewModel();

    expect(() =>
      viewModel.proposeEqualSplit({
        title: 'Reparation toiture',
        totalAmountInCents: 12500,
        participants: new Set(),
      }),
    ).toThrow('Un groupe doit etre initialise avant de proposer une depense.');
    expect(proposalPort.commands).toEqual([]);
  });

  it('starts with an empty participant selection in the Signal Form model', async () => {
    const viewModel = createViewModel();
    viewModel.initialize('group-1');
    await waitFor(() => viewModel.members().length === 2);

    expect(viewModel.proposalForm().value()).toEqual({
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
      cumulativeTiers: {
        intermediateTiers: [],
        finalParticipants: [],
      },
    });
    expect(viewModel.proposalForm().invalid()).toBe(true);
  });

  it.each(['CUSTOM'] as const)(
    'blocks submission while %s fields are unavailable',
    async (allocationMode) => {
      const viewModel = createViewModel();
      viewModel.initialize('group-1');
      viewModel.proposalForm.title().value.set('Toiture');
      viewModel.proposalForm.amountInEuros().value.set('12,50');
      viewModel.toggleParticipant('alice@example.com');

      viewModel.proposalForm.allocationMode().value.set(allocationMode);

      expect(viewModel.proposalForm().invalid()).toBe(true);
      await expect(submit(viewModel.proposalForm)).resolves.toBe(false);
      expect(proposalPort.commands).toEqual([]);
    },
  );

  it('preserves the shared fields and equal draft when returning to equal allocation', async () => {
    const viewModel = createViewModel();
    viewModel.initialize('group-1');
    viewModel.proposalForm.title().value.set('  Toiture  ');
    viewModel.proposalForm.amountInEuros().value.set('12,50');
    viewModel.toggleParticipant('alice@example.com');

    viewModel.proposalForm.allocationMode().value.set('CUSTOM');
    expect(viewModel.proposalForm().value()).toEqual({
      title: '  Toiture  ',
      amountInEuros: '12,50',
      allocationMode: 'CUSTOM',
      equal: {
        participants: ['alice@example.com'],
      },
      equalWithCaps: {
        participants: [],
        maximumAmountsInEuros: {},
      },
      cumulativeTiers: {
        intermediateTiers: [],
        finalParticipants: [],
      },
    });

    viewModel.proposalForm.allocationMode().value.set('EQUAL');

    await expect(submit(viewModel.proposalForm)).resolves.toBe(true);
    await waitFor(() => proposalPort.commands.length === 1);
    expect(proposalPort.commands).toEqual([
      {
        groupId: 'group-1',
        title: 'Toiture',
        totalAmountInCents: 1250,
        allocation: {
          type: 'EQUAL',
          participants: new Set(['alice@example.com']),
        },
      },
    ]);
  });

  it('submits an equal split with one capped participant', async () => {
    const viewModel = createViewModel();
    viewModel.initialize('group-1');
    viewModel.proposalForm.title().value.set('  Toiture  ');
    viewModel.proposalForm.amountInEuros().value.set('125');
    viewModel.proposalForm.allocationMode().value.set('EQUAL_WITH_CAPS');
    viewModel.toggleEqualWithCapsParticipant('alice@example.com');
    viewModel.toggleEqualWithCapsParticipant('bob@example.com');
    viewModel.setEqualWithCapsMaximum('bob@example.com', '25,50');

    await expect(submit(viewModel.proposalForm)).resolves.toBe(true);
    await waitFor(() => proposalPort.commands.length === 1);

    expect(proposalPort.commands).toEqual([
      {
        groupId: 'group-1',
        title: 'Toiture',
        totalAmountInCents: 12500,
        allocation: {
          type: 'EQUAL_WITH_CAPS',
          participants: new Set(['alice@example.com', 'bob@example.com']),
          capsInCentsByMember: new Map([['bob@example.com', 2550]]),
        },
      },
    ]);
  });

  it('submits an equal split with caps without requiring any cap', async () => {
    const viewModel = createViewModel();
    viewModel.initialize('group-1');
    viewModel.proposalForm.title().value.set('Toiture');
    viewModel.proposalForm.amountInEuros().value.set('125');
    viewModel.proposalForm.allocationMode().value.set('EQUAL_WITH_CAPS');
    viewModel.toggleEqualWithCapsParticipant('alice@example.com');

    await expect(submit(viewModel.proposalForm)).resolves.toBe(true);
    await waitFor(() => proposalPort.commands.length === 1);

    expect(proposalPort.commands[0]?.allocation).toEqual({
      type: 'EQUAL_WITH_CAPS',
      participants: new Set(['alice@example.com']),
      capsInCentsByMember: new Map(),
    });
  });

  it('requires an equal with caps participant', async () => {
    const viewModel = createViewModel();
    viewModel.initialize('group-1');
    viewModel.proposalForm.title().value.set('Toiture');
    viewModel.proposalForm.amountInEuros().value.set('125');
    viewModel.proposalForm.allocationMode().value.set('EQUAL_WITH_CAPS');

    expect(viewModel.proposalForm().invalid()).toBe(true);
    await expect(submit(viewModel.proposalForm)).resolves.toBe(false);
    expect(proposalPort.commands).toEqual([]);
  });

  it('rejects an invalid equal with caps maximum amount', async () => {
    const viewModel = createViewModel();
    viewModel.initialize('group-1');
    viewModel.proposalForm.title().value.set('Toiture');
    viewModel.proposalForm.amountInEuros().value.set('125');
    viewModel.proposalForm.allocationMode().value.set('EQUAL_WITH_CAPS');
    viewModel.toggleEqualWithCapsParticipant('alice@example.com');
    viewModel.toggleEqualWithCapsParticipant('bob@example.com');
    viewModel.setEqualWithCapsMaximum('bob@example.com', '25,555');

    expect(viewModel.proposalForm().invalid()).toBe(true);
    await expect(submit(viewModel.proposalForm)).resolves.toBe(false);
    expect(proposalPort.commands).toEqual([]);
  });

  it('requires at least one uncapped participant', async () => {
    const viewModel = createViewModel();
    viewModel.initialize('group-1');
    viewModel.proposalForm.title().value.set('Toiture');
    viewModel.proposalForm.amountInEuros().value.set('125');
    viewModel.proposalForm.allocationMode().value.set('EQUAL_WITH_CAPS');
    viewModel.toggleEqualWithCapsParticipant('alice@example.com');
    viewModel.toggleEqualWithCapsParticipant('bob@example.com');
    viewModel.setEqualWithCapsMaximum('alice@example.com', '50');
    viewModel.setEqualWithCapsMaximum('bob@example.com', '25,50');

    expect(viewModel.proposalForm().invalid()).toBe(true);
    await expect(submit(viewModel.proposalForm)).resolves.toBe(false);
    expect(proposalPort.commands).toEqual([]);
  });

  it('keeps equal with caps selection and amounts independent from equal', () => {
    const viewModel = createViewModel();
    viewModel.initialize('group-1');
    viewModel.toggleParticipant('alice@example.com');
    viewModel.proposalForm.allocationMode().value.set('EQUAL_WITH_CAPS');
    viewModel.toggleEqualWithCapsParticipant('bob@example.com');
    viewModel.setEqualWithCapsMaximum('bob@example.com', '25,50');

    viewModel.proposalForm.allocationMode().value.set('EQUAL');
    viewModel.proposalForm.allocationMode().value.set('EQUAL_WITH_CAPS');

    expect(viewModel.proposalForm().value().equal).toEqual({
      participants: ['alice@example.com'],
    });
    expect(viewModel.proposalForm().value().equalWithCaps).toEqual({
      participants: ['bob@example.com'],
      maximumAmountsInEuros: {
        'bob@example.com': '25,50',
      },
    });
  });

  it('submits cumulative tiers with the final threshold set to the total amount', async () => {
    const viewModel = createViewModel();
    viewModel.initialize('group-1');
    viewModel.proposalForm.title().value.set('  Toiture  ');
    viewModel.proposalForm.amountInEuros().value.set('101');
    viewModel.proposalForm.allocationMode().value.set('CUMULATIVE_TIERS');
    viewModel.addCumulativeIntermediateTier();
    viewModel.setCumulativeIntermediateThreshold(0, '40');
    viewModel.toggleCumulativeIntermediateParticipant(0, 'alice@example.com');
    viewModel.toggleCumulativeIntermediateParticipant(0, 'bob@example.com');
    viewModel.toggleCumulativeFinalParticipant('alice@example.com');

    await expect(submit(viewModel.proposalForm)).resolves.toBe(true);
    await waitFor(() => proposalPort.commands.length === 1);

    expect(proposalPort.commands).toEqual([
      {
        groupId: 'group-1',
        title: 'Toiture',
        totalAmountInCents: 10100,
        allocation: {
          type: 'CUMULATIVE_TIERS',
          tiers: [
            {
              upToAmountInCents: 4000,
              participants: new Set(['alice@example.com', 'bob@example.com']),
            },
            {
              upToAmountInCents: 10100,
              participants: new Set(['alice@example.com']),
            },
          ],
        },
      },
    ]);
  });

  it('requires participants in every cumulative tier', async () => {
    const viewModel = createViewModel();
    prepareCumulativeProposal(viewModel, '101');
    viewModel.addCumulativeIntermediateTier();
    viewModel.setCumulativeIntermediateThreshold(0, '40');
    viewModel.toggleCumulativeFinalParticipant('alice@example.com');

    expect(viewModel.proposalForm().invalid()).toBe(true);
    await expect(submit(viewModel.proposalForm)).resolves.toBe(false);
    expect(proposalPort.commands).toEqual([]);
  });

  it('requires participants in the final cumulative tier', async () => {
    const viewModel = createViewModel();
    prepareCumulativeProposal(viewModel, '101');

    expect(viewModel.proposalForm().invalid()).toBe(true);
    await expect(submit(viewModel.proposalForm)).resolves.toBe(false);
    expect(proposalPort.commands).toEqual([]);
  });

  it.each([
    ['an invalid threshold', ['40,555']],
    ['a zero threshold', ['0']],
    ['a negative threshold', ['-1']],
    ['non-increasing thresholds', ['40', '40']],
    ['a threshold equal to the total', ['101']],
    ['a threshold above the total', ['102']],
  ])('rejects %s', async (_description, thresholds) => {
    const viewModel = createViewModel();
    prepareCumulativeProposal(viewModel, '101');
    thresholds.forEach((threshold, index) => {
      viewModel.addCumulativeIntermediateTier();
      viewModel.setCumulativeIntermediateThreshold(index, threshold);
      viewModel.toggleCumulativeIntermediateParticipant(index, 'alice@example.com');
    });
    viewModel.toggleCumulativeFinalParticipant('bob@example.com');

    expect(viewModel.proposalForm().invalid()).toBe(true);
    await expect(submit(viewModel.proposalForm)).resolves.toBe(false);
    expect(proposalPort.commands).toEqual([]);
  });

  it('rejects a cumulative tier that would give a zero-cent share', async () => {
    const viewModel = createViewModel();
    prepareCumulativeProposal(viewModel, '0,01');
    viewModel.toggleCumulativeFinalParticipant('alice@example.com');
    viewModel.toggleCumulativeFinalParticipant('bob@example.com');

    expect(viewModel.proposalForm().invalid()).toBe(true);
    await expect(submit(viewModel.proposalForm)).resolves.toBe(false);
    expect(proposalPort.commands).toEqual([]);
  });

  it('adds and removes intermediate tiers immutably while guarding invalid indexes', () => {
    const viewModel = createViewModel();
    prepareCumulativeProposal(viewModel, '101');
    viewModel.addCumulativeIntermediateTier();
    viewModel.setCumulativeIntermediateThreshold(0, '40');
    viewModel.toggleCumulativeIntermediateParticipant(0, 'alice@example.com');
    viewModel.addCumulativeIntermediateTier();
    viewModel.setCumulativeIntermediateThreshold(1, '70');
    viewModel.toggleCumulativeIntermediateParticipant(1, 'bob@example.com');
    const beforeInvalidIndexes = viewModel.proposalForm().value().cumulativeTiers;

    viewModel.setCumulativeIntermediateThreshold(3, '90');
    viewModel.toggleCumulativeIntermediateParticipant(3, 'alice@example.com');
    viewModel.removeCumulativeIntermediateTier(3);

    expect(viewModel.proposalForm().value().cumulativeTiers).toEqual(beforeInvalidIndexes);

    viewModel.removeCumulativeIntermediateTier(0);
    expect(viewModel.proposalForm().value().cumulativeTiers.intermediateTiers).toEqual([
      {
        upToAmountInEuros: '70',
        participants: ['bob@example.com'],
      },
    ]);
  });

  it('preserves the cumulative tiers draft between allocation modes', () => {
    const viewModel = createViewModel();
    prepareCumulativeProposal(viewModel, '101');
    viewModel.addCumulativeIntermediateTier();
    viewModel.setCumulativeIntermediateThreshold(0, '40');
    viewModel.toggleCumulativeIntermediateParticipant(0, 'alice@example.com');
    viewModel.toggleCumulativeFinalParticipant('bob@example.com');

    viewModel.proposalForm.allocationMode().value.set('EQUAL');
    viewModel.proposalForm.allocationMode().value.set('CUMULATIVE_TIERS');

    expect(viewModel.proposalForm().value().cumulativeTiers).toEqual({
      intermediateTiers: [
        {
          upToAmountInEuros: '40',
          participants: ['alice@example.com'],
        },
      ],
      finalParticipants: ['bob@example.com'],
    });
  });

  it('validates non-blank title, amount and selected participants before submitting', async () => {
    const viewModel = createViewModel();
    viewModel.initialize('group-1');
    await waitFor(() => viewModel.members().length === 2);

    viewModel.proposalForm.title().value.set('   ');
    viewModel.proposalForm.amountInEuros().value.set('12,345');
    expect(viewModel.proposalForm().invalid()).toBe(true);

    await expect(submit(viewModel.proposalForm)).resolves.toBe(false);
    expect(proposalPort.commands).toEqual([]);

    viewModel.proposalForm.title().value.set('  Toiture  ');
    viewModel.proposalForm.amountInEuros().value.set('12,50');
    viewModel.toggleParticipant('alice@example.com');
    expect(viewModel.proposalForm().valid()).toBe(true);

    await expect(submit(viewModel.proposalForm)).resolves.toBe(true);
    await waitFor(() => proposalPort.commands.length === 1);

    expect(proposalPort.commands).toEqual([
      {
        groupId: 'group-1',
        title: 'Toiture',
        totalAmountInCents: 1250,
        allocation: {
          type: 'EQUAL',
          participants: new Set(['alice@example.com']),
        },
      },
    ]);
  });

  it.each(['12', '12,5', '12,50', '12.50'])('accepts the amount format %s', (amount) => {
    const viewModel = createViewModel();
    viewModel.initialize('group-1');
    viewModel.proposalForm.title().value.set('Toiture');
    viewModel.proposalForm.amountInEuros().value.set(amount);
    viewModel.toggleParticipant('alice@example.com');

    expect(viewModel.proposalForm().valid()).toBe(true);
  });

  const createViewModel = () => {
    const viewModel = new ExpenseProposalWidgetViewModel(port, proposalPort, queryClient, injector);
    TestBed.tick();
    return viewModel;
  };

  const prepareCumulativeProposal = (
    viewModel: ExpenseProposalWidgetViewModel,
    totalAmountInEuros: string,
  ): void => {
    viewModel.proposalForm.title().value.set('Toiture');
    viewModel.proposalForm.amountInEuros().value.set(totalAmountInEuros);
    viewModel.proposalForm.allocationMode().value.set('CUMULATIVE_TIERS');
  };
});

class StubGroupMembersPort extends GroupMembersPort {
  members: readonly GroupMember[] = [
    { member: 'alice@example.com', joinedAt: new Date('2026-08-01T10:00:00.000Z') },
    { member: 'bob@example.com', joinedAt: new Date('2026-08-02T10:00:00.000Z') },
  ];
  requestedGroupIds: string[] = [];
  failure: Error | null = null;

  override listByGroup(groupId: string): Observable<readonly GroupMember[]> {
    this.requestedGroupIds.push(groupId);
    const failure = this.failure;

    return failure === null ? of(this.members) : throwError(() => failure);
  }
}

class StubExpenseProposalPort extends ExpenseProposalPort {
  commands: ExpenseProposalCommand[] = [];
  failure: Error | null = null;
  private deferred: Promise<void> | null = null;
  private resolveDeferredPromise: (() => void) | null = null;

  override propose(command: ExpenseProposalCommand): Promise<void> {
    this.commands.push(command);
    if (this.failure) return Promise.reject(this.failure);
    return this.deferred ?? Promise.resolve();
  }

  useDeferredResult(): void {
    this.deferred = new Promise((resolve) => {
      this.resolveDeferredPromise = resolve;
    });
  }

  resolveDeferred(): void {
    this.resolveDeferredPromise?.();
  }
}

const waitFor = async (condition: () => boolean): Promise<void> => {
  for (let attempt = 0; attempt < 50; attempt += 1) {
    if (condition()) return;
    await new Promise((resolve) => setTimeout(resolve));
  }

  throw new Error('La condition attendue n a pas ete atteinte.');
};
