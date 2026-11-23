import { FinancialFormatter } from '../../i18n/financial-formatter';
import { provideLocalization } from '../../i18n/provide-localization';
import { Injector } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';

import { MemberBalanceImpactHistoryPort } from '../../../application/ledger/member-balance-impact-history.port';
import type { MemberBalanceImpactHistory } from '../../../domain/ledger/member-balance-impact-history';
import { MemberBalanceImpactHistoryWidgetViewModel } from './member-balance-impact-history-widget.view-model';

describe('MemberBalanceImpactHistoryWidgetViewModel', () => {
  let port: StubMemberBalanceImpactHistoryPort;
  let injector: Injector;

  beforeEach(() => {
    port = new StubMemberBalanceImpactHistoryPort();
    TestBed.configureTestingModule({
      providers: [
        provideLocalization(),
        provideTanStackQuery(new QueryClient({ defaultOptions: { queries: { retry: false } } })),
      ],
    });
    injector = TestBed.inject(Injector);
  });

  describe('load', () => {
    it('does not request the history before the user asks for it', () => {
      createViewModel();

      expect(port.requests).toEqual([]);
    });

    it('loads and presents every operation that reduced the selected member debt', async () => {
      const viewModel = createViewModel();

      viewModel.load('group-1', 'alice@example.com');
      await waitFor(() => viewModel.isReady());

      expect(port.requests).toEqual([{ groupId: 'group-1', member: 'alice@example.com' }]);
      expect(viewModel.impacts()).toEqual([
        {
          operationId: 'reimbursement-1',
          key: 'DIRECT_REIMBURSEMENT:reimbursement-1:DEBT_REDUCTION',
          label: 'balanceImpact.types.reimbursement',
          amount: '12,50\u00a0€',
          occurredAt: expect.stringContaining('2026'),
        },
        {
          operationId: 'expense-1',
          key: 'COMPENSATING_EXPENSE:expense-1:DEBT_REDUCTION',
          label: 'balanceImpact.types.expense',
          amount: '5,00\u00a0€',
          occurredAt: expect.stringContaining('2026'),
        },
        {
          operationId: 'withdrawal-1',
          key: 'CASH_POOL_WITHDRAWAL:withdrawal-1:CREDIT_REDUCTION',
          label: 'balanceImpact.types.withdrawalCreditReduction',
          amount: '3,00\u00a0€',
          occurredAt: expect.stringContaining('2026'),
        },
      ]);
    });

    it('exposes a load error', async () => {
      port.failure = new Error('Historique indisponible');
      const viewModel = createViewModel();

      viewModel.load('group-1', 'alice@example.com');
      await waitFor(() => viewModel.hasLoadError());

      expect(viewModel.errorMessage()).toBe('Historique indisponible');
    });
  });

  describe('retry', () => {
    it('retries the selected member history', async () => {
      port.failure = new Error('Premier échec');
      const viewModel = createViewModel();
      viewModel.load('group-1', 'alice@example.com');
      await waitFor(() => viewModel.hasLoadError());

      port.failure = null;
      viewModel.retry();
      await waitFor(() => viewModel.isReady());

      expect(port.requests).toEqual([
        { groupId: 'group-1', member: 'alice@example.com' },
        { groupId: 'group-1', member: 'alice@example.com' },
      ]);
    });
  });

  const createViewModel = () =>
    new MemberBalanceImpactHistoryWidgetViewModel(
      port,
      injector,
      TestBed.inject(FinancialFormatter),
    );
});

class StubMemberBalanceImpactHistoryPort extends MemberBalanceImpactHistoryPort {
  result: MemberBalanceImpactHistory = {
    groupId: 'group-1',
    member: 'alice@example.com',
    impacts: [
      impact('DIRECT_REIMBURSEMENT', 'reimbursement-1', 1250, '2026-04-03T10:00:00Z'),
      impact('COMPENSATING_EXPENSE', 'expense-1', 500, '2026-04-04T10:00:00Z'),
      impact(
        'CASH_POOL_WITHDRAWAL',
        'withdrawal-1',
        300,
        '2026-04-05T10:00:00Z',
        'CREDIT_REDUCTION',
      ),
    ],
  };
  failure: Error | null = null;
  readonly requests: { groupId: string; member: string }[] = [];

  override async get(groupId: string, member: string): Promise<MemberBalanceImpactHistory> {
    this.requests.push({ groupId, member });
    if (this.failure !== null) throw this.failure;
    return this.result;
  }
}

const impact = (
  type: 'DIRECT_REIMBURSEMENT' | 'COMPENSATING_EXPENSE' | 'CASH_POOL_WITHDRAWAL',
  operationId: string,
  amountInCents: number,
  occurredAt: string,
  effect: 'DEBT_REDUCTION' | 'DEBT_INCREASE' | 'CREDIT_REDUCTION' = 'DEBT_REDUCTION',
) => ({ type, effect, operationId, amountInCents, occurredAt: new Date(occurredAt) });

const waitFor = async (condition: () => boolean): Promise<void> => {
  for (let attempt = 0; attempt < 50; attempt += 1) {
    TestBed.tick();
    if (condition()) return;
    await new Promise((resolve) => setTimeout(resolve));
  }

  throw new Error('La condition attendue n a pas ete atteinte.');
};
