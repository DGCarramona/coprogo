import { Injector } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';

import { ExpenseDetailPort } from '../../application/expense/expense-detail.port';
import type { ExpenseDetail } from '../../domain/expense/expense-detail';
import { ExpenseDetailPageViewModel } from './expense-detail-page.view-model';

describe('ExpenseDetailPageViewModel', () => {
  let port: StubExpenseDetailPort;
  let injector: Injector;

  beforeEach(() => {
    port = new StubExpenseDetailPort();
    TestBed.configureTestingModule({
      providers: [
        provideTanStackQuery(new QueryClient({ defaultOptions: { queries: { retry: false } } })),
      ],
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
        totalAmount: '60,00\u00a0€',
        createdAt: expect.stringContaining('2026'),
        statusLabel: 'En attente de validation',
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

  const createViewModel = (route: ActivatedRoute) =>
    new ExpenseDetailPageViewModel(route, port, injector);
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

const expenseDetail = (): ExpenseDetail => ({
  id: 'expense-1',
  title: 'Réfection du toit',
  createdBy: 'alice@example.com',
  totalAmountInCents: 6000,
  createdAt: new Date('2026-07-01T10:00:00Z'),
  status: 'PROPOSED',
  participations: [
    { member: 'alice@example.com', amountInCents: 2000, status: 'APPROVED' },
    { member: 'bob@example.com', amountInCents: 2000, status: 'PENDING' },
    { member: 'charlie@example.com', amountInCents: 2000, status: 'REFUSED' },
  ],
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
