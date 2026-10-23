import { Injector } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';

import { CashPoolHistoryPort } from '../../../application/ledger/cash-pool-history.port';
import type { CashPoolHistory } from '../../../domain/ledger/cash-pool-history';
import { CashPoolHistoryWidgetViewModel } from './cash-pool-history-widget.view-model';

describe('CashPoolHistoryWidgetViewModel', () => {
  let port: StubCashPoolHistoryPort;
  let injector: Injector;

  beforeEach(() => {
    port = new StubCashPoolHistoryPort();
    TestBed.configureTestingModule({
      providers: [
        provideTanStackQuery(new QueryClient({ defaultOptions: { queries: { retry: false } } })),
      ],
    });
    injector = TestBed.inject(Injector);
  });

  describe('load', () => {
    it('presents income distributions and withdrawals with their distinct details', async () => {
      const viewModel = createViewModel();

      viewModel.load('group-1');
      await waitFor(() => viewModel.isReady());

      expect(port.groupIds).toEqual(['group-1']);
      expect(viewModel.operations()).toEqual([
        {
          type: 'WITHDRAWAL',
          operationId: 'withdrawal-1',
          occurredAt: expect.stringContaining('2026'),
          label: 'Retrait par alice@example.com',
          amount: '70,00\u00a0€',
          ownRevenueShareConsumed: '60,00\u00a0€',
          debtReduction: '10,00\u00a0€',
          hasDebtReduction: true,
        },
        {
          type: 'INCOME',
          operationId: 'income-1',
          occurredAt: expect.stringContaining('2026'),
          label: 'Revenu ajouté',
          amount: '100,00\u00a0€',
          allocations: [
            { member: 'alice@example.com', amount: '60,00\u00a0€' },
            { member: 'bob@example.com', amount: '40,00\u00a0€' },
          ],
        },
      ]);
    });

    it('exposes an empty history', async () => {
      port.result = { groupId: 'group-1', operations: [] };
      const viewModel = createViewModel();

      viewModel.load('group-1');
      await waitFor(() => viewModel.isReady());

      expect(viewModel.operations()).toEqual([]);
    });

    it('exposes and retries a load error', async () => {
      port.failure = new Error('Historique indisponible');
      const viewModel = createViewModel();
      viewModel.load('group-1');
      await waitFor(() => viewModel.hasLoadError());

      port.failure = null;
      viewModel.retry();
      await waitFor(() => viewModel.isReady());

      expect(port.groupIds).toEqual(['group-1', 'group-1']);
    });
  });

  const createViewModel = (): CashPoolHistoryWidgetViewModel =>
    new CashPoolHistoryWidgetViewModel(port, injector);
});

class StubCashPoolHistoryPort extends CashPoolHistoryPort {
  result: CashPoolHistory = {
    groupId: 'group-1',
    operations: [
      {
        type: 'WITHDRAWAL',
        operationId: 'withdrawal-1',
        occurredAt: new Date('2026-10-02T10:00:00Z'),
        withdrawnBy: 'alice@example.com',
        amountInCents: 7000,
        ownRevenueShareConsumedInCents: 6000,
      },
      {
        type: 'INCOME',
        operationId: 'income-1',
        occurredAt: new Date('2026-10-01T10:00:00Z'),
        amountInCents: 10000,
        allocations: [
          { member: 'alice@example.com', amountInCents: 6000 },
          { member: 'bob@example.com', amountInCents: 4000 },
        ],
      },
    ],
  };
  failure: Error | null = null;
  readonly groupIds: string[] = [];

  override async get(groupId: string): Promise<CashPoolHistory> {
    this.groupIds.push(groupId);
    if (this.failure !== null) throw this.failure;
    return this.result;
  }
}

const waitFor = async (condition: () => boolean): Promise<void> => {
  for (let attempt = 0; attempt < 50; attempt += 1) {
    TestBed.tick();
    if (condition()) return;
    await new Promise((resolve) => setTimeout(resolve));
  }
  throw new Error('La condition attendue n’a pas été atteinte.');
};
