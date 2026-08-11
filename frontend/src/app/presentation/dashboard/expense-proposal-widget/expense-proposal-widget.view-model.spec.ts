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
    });
    expect(viewModel.proposalForm().invalid()).toBe(true);
  });

  it.each(['EQUAL_WITH_CAPS', 'CUMULATIVE_TIERS', 'CUSTOM'] as const)(
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
