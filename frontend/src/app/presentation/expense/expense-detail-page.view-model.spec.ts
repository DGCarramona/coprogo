import { Injector } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';

import { ExpenseDetailPort } from '../../application/expense/expense-detail.port';
import { ExpenseParticipationDecisionPort } from '../../application/expense/expense-participation-decision.port';
import type { ExpenseDetail } from '../../domain/expense/expense-detail';
import { ExpenseDetailPageViewModel } from './expense-detail-page.view-model';

describe('ExpenseDetailPageViewModel', () => {
  let port: StubExpenseDetailPort;
  let decisionPort: StubExpenseParticipationDecisionPort;
  let queryClient: QueryClient;
  let injector: Injector;

  beforeEach(() => {
    port = new StubExpenseDetailPort();
    decisionPort = new StubExpenseParticipationDecisionPort();
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    TestBed.configureTestingModule({
      providers: [provideTanStackQuery(queryClient)],
    });
    injector = TestBed.inject(Injector);
  });

  describe('detail', () => {
    it('loads and presents the expense selected by the route', async () => {
      const viewModel = createViewModel(routeWith('group-1', 'expense-1'));
      await waitFor(() => viewModel.isReady());

      expect(port.requests).toEqual([{ groupId: 'group-1', expenseId: 'expense-1' }]);
      expect(viewModel.detail()).toEqual({
        id: 'expense-1',
        title: 'Réfection du toit',
        createdBy: 'alice@example.com',
        canDecideParticipation: true,
        totalAmount: '60,00\u00a0€',
        createdAt: expect.stringContaining('2026'),
        statusLabel: 'En attente de validation',
        invalidation: null,
        participations: [
          {
            member: 'alice@example.com',
            amount: '20,00\u00a0€',
            statusLabel: 'Validée',
            tone: 'approved',
          },
          {
            member: 'bob@example.com',
            amount: '20,00\u00a0€',
            statusLabel: 'À valider',
            tone: 'pending',
          },
          {
            member: 'charlie@example.com',
            amount: '20,00\u00a0€',
            statusLabel: 'Refusée',
            tone: 'refused',
          },
        ],
      });
    });

    it('presents the refusal behind an expense that must be replaced', async () => {
      port.result = expenseDetail({
        status: 'INVALIDATED',
        canDecideParticipation: false,
        refusal: {
          member: 'charlie@example.com',
          refusedAt: new Date('2026-07-02T08:30:00Z'),
          reason: 'Montant incorrect',
        },
      });

      const viewModel = createViewModel(routeWith('group-1', 'expense-1'));
      await waitFor(() => viewModel.isReady());

      expect(viewModel.detail()?.invalidation).toEqual({
        refusedBy: 'charlie@example.com',
        refusedAt: expect.stringContaining('2026'),
        reason: 'Montant incorrect',
      });
    });

    it('rejects an invalidated expense without its mandatory refusal', async () => {
      port.result = expenseDetail({
        status: 'INVALIDATED',
        canDecideParticipation: false,
        refusal: null,
      });

      const viewModel = createViewModel(routeWith('group-1', 'expense-1'));
      await waitFor(() => viewModel.hasLoadError());

      expect(viewModel.errorMessage()).toBe('Une dépense invalidée doit contenir son refus.');
    });

    it('reports missing route identifiers without querying', () => {
      const viewModel = createViewModel(routeWith(null, null));

      expect(viewModel.hasLoadError()).toBe(true);
      expect(viewModel.errorMessage()).toBe('La dépense demandée est incomplète.');
      expect(port.requests).toEqual([]);
    });

    it('exposes a loading failure', async () => {
      port.failure = new Error('Détail indisponible');
      const viewModel = createViewModel(routeWith('group-1', 'expense-1'));

      await waitFor(() => viewModel.hasLoadError());

      expect(viewModel.errorMessage()).toBe('Détail indisponible');
    });
  });

  describe('retry', () => {
    it('retries the same expense after a failure', async () => {
      port.failure = new Error('Premier échec');
      const viewModel = createViewModel(routeWith('group-1', 'expense-1'));
      await waitFor(() => viewModel.hasLoadError());

      port.failure = null;
      viewModel.retry();
      await waitFor(() => viewModel.isReady());

      expect(port.requests).toEqual([
        { groupId: 'group-1', expenseId: 'expense-1' },
        { groupId: 'group-1', expenseId: 'expense-1' },
      ]);
    });
  });

  describe('canDecide', () => {
    it('uses the decision capability returned by the backend', async () => {
      const viewModel = createViewModel(routeWith('group-1', 'expense-1'));
      await waitFor(() => viewModel.isReady());

      expect(viewModel.canDecide()).toBe(true);

      port.result = expenseDetail({ canDecideParticipation: false });
      viewModel.retry();
      await waitFor(() => viewModel.detail()?.canDecideParticipation === false);

      expect(viewModel.canDecide()).toBe(false);
    });
  });

  describe('approve', () => {
    it('approves without a refusal reason and refreshes the detail and list', async () => {
      queryClient.setQueryData(['groups', 'group-1', 'expenses'], ['cached']);
      const viewModel = createViewModel(routeWith('group-1', 'expense-1'));
      await waitFor(() => viewModel.isReady());

      await viewModel.approve();

      expect(decisionPort.approvals).toEqual([{ groupId: 'group-1', expenseId: 'expense-1' }]);
      expect(port.requests).toEqual([
        { groupId: 'group-1', expenseId: 'expense-1' },
        { groupId: 'group-1', expenseId: 'expense-1' },
      ]);
      expect(queryClient.getQueryState(['groups', 'group-1', 'expenses'])?.isInvalidated).toBe(
        true,
      );
    });

    it('does not call the decision port when the backend denies the capability', async () => {
      port.result = expenseDetail({ canDecideParticipation: false });
      const viewModel = createViewModel(routeWith('group-1', 'expense-1'));
      await waitFor(() => viewModel.isReady());

      await viewModel.approve();

      expect(decisionPort.approvals).toEqual([]);
      expect(viewModel.decisionError()).toBe(
        'Aucune participation en attente ne peut être validée.',
      );
    });
  });

  describe('refuse', () => {
    it.each([
      {
        name: 'with a trimmed reason',
        enteredReason: '  Montant incorrect  ',
        expectedReason: 'Montant incorrect',
      },
      { name: 'without a reason', enteredReason: '   ', expectedReason: null },
    ])('refuses $name', async ({ enteredReason, expectedReason }) => {
      const viewModel = createViewModel(routeWith('group-1', 'expense-1'));
      await waitFor(() => viewModel.isReady());
      viewModel.rejectionReason.set(enteredReason);

      await viewModel.refuse();

      expect(decisionPort.refusals).toEqual([
        { groupId: 'group-1', expenseId: 'expense-1', reason: expectedReason },
      ]);
    });

    it('keeps the reason and exposes a decision failure', async () => {
      decisionPort.failure = new Error('Décision indisponible');
      const viewModel = createViewModel(routeWith('group-1', 'expense-1'));
      await waitFor(() => viewModel.isReady());
      viewModel.rejectionReason.set('Montant incorrect');

      await viewModel.refuse();

      expect(viewModel.rejectionReason()).toBe('Montant incorrect');
      expect(viewModel.decisionError()).toBe('Décision indisponible');
    });
  });

  const createViewModel = (route: ActivatedRoute) =>
    new ExpenseDetailPageViewModel(route, port, decisionPort, queryClient, injector);
});

class StubExpenseDetailPort extends ExpenseDetailPort {
  result: ExpenseDetail = expenseDetail();
  failure: Error | null = null;
  readonly requests: { groupId: string; expenseId: string }[] = [];

  override async get(groupId: string, expenseId: string): Promise<ExpenseDetail> {
    this.requests.push({ groupId, expenseId });
    if (this.failure !== null) throw this.failure;
    return this.result;
  }
}

class StubExpenseParticipationDecisionPort extends ExpenseParticipationDecisionPort {
  readonly approvals: { readonly groupId: string; readonly expenseId: string }[] = [];
  readonly refusals: {
    readonly groupId: string;
    readonly expenseId: string;
    readonly reason: string | null;
  }[] = [];
  failure: Error | null = null;

  override async approve(groupId: string, expenseId: string): Promise<void> {
    this.approvals.push({ groupId, expenseId });
    if (this.failure !== null) throw this.failure;
  }

  override async refuse(groupId: string, expenseId: string, reason: string | null): Promise<void> {
    this.refusals.push({ groupId, expenseId, reason });
    if (this.failure !== null) throw this.failure;
  }
}

const expenseDetail = (overrides: Partial<ExpenseDetail> = {}): ExpenseDetail => ({
  id: 'expense-1',
  title: 'Réfection du toit',
  createdBy: 'alice@example.com',
  canDecideParticipation: true,
  totalAmountInCents: 6000,
  createdAt: new Date('2026-07-01T10:00:00Z'),
  status: 'PROPOSED',
  refusal: null,
  participations: [
    { member: 'alice@example.com', amountInCents: 2000, status: 'APPROVED' },
    { member: 'bob@example.com', amountInCents: 2000, status: 'PENDING' },
    { member: 'charlie@example.com', amountInCents: 2000, status: 'REFUSED' },
  ],
  ...overrides,
});

const routeWith = (groupId: string | null, expenseId: string | null): ActivatedRoute =>
  ({
    snapshot: {
      paramMap: convertToParamMap({
        ...(groupId === null ? {} : { groupId }),
        ...(expenseId === null ? {} : { expenseId }),
      }),
    },
  }) as ActivatedRoute;

const waitFor = async (condition: () => boolean): Promise<void> => {
  for (let attempt = 0; attempt < 50; attempt += 1) {
    TestBed.tick();
    if (condition()) return;
    await new Promise((resolve) => setTimeout(resolve));
  }
  throw new Error('La condition attendue n a pas ete atteinte.');
};
