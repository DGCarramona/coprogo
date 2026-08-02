import { Injector } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';

import { ExpenseListWidgetViewModel } from './expense-list-widget.view-model';
import { ExpenseListPort } from '../../../application/expense/expense-list.port';
import type { ExpenseSummary } from '../../../domain/expense/expense-summary';

describe('ExpenseListWidgetViewModel', () => {
  let queryClient: QueryClient;
  let port: StubExpenseListPort;
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
    port = new StubExpenseListPort();
    injector = TestBed.inject(Injector);
  });

  it('refreshes expenses when the scoped query is invalidated', async () => {
    const vm = createViewModel();
    vm.initialize('group-1');
    await waitFor(() => vm.isReady());
    port.result = [expense({ id: 'e2', title: 'Toiture', totalAmountInCents: 1250 })];

    await queryClient.invalidateQueries({ queryKey: ['groups', 'group-1', 'expenses'] });
    await waitFor(
      () => port.requestedGroupIds.length === 2 && vm.expenses()[0]?.title === 'Toiture',
    );

    expect(vm.expenses()).toEqual([
      { title: 'Toiture', amount: '12,50\u00a0€', createdBy: 'alice@example.com' },
    ]);
  });

  it('loads expenses for the given group', async () => {
    const vm = createViewModel();

    vm.initialize('group-1');
    await waitFor(() => vm.isReady());

    expect(vm.expenses()).toEqual([
      { title: 'Courses', amount: '15,00\u00a0€', createdBy: 'alice@example.com' },
    ]);
    expect(port.requestedGroupIds).toEqual(['group-1']);
  });

  it('exposes empty list when no expenses', async () => {
    port.result = [];
    const vm = createViewModel();

    vm.initialize('group-1');
    await waitFor(() => vm.isReady());

    expect(vm.expenses()).toEqual([]);
  });

  it('exposes a load error', async () => {
    port.failure = new Error('Erreur reseau');
    const vm = createViewModel();

    vm.initialize('group-1');
    await waitFor(() => vm.hasLoadError());

    expect(vm.hasLoadError()).toBe(true);
    expect(vm.errorMessage()).toBe('Erreur reseau');
  });

  it('is loading while fetching', async () => {
    let resolvePromise!: (value: readonly ExpenseSummary[]) => void;
    port.resultPromise = new Promise((resolve) => {
      resolvePromise = resolve;
    });
    const vm = createViewModel();

    vm.initialize('group-1');
    await waitFor(() => vm.isLoading());

    expect(vm.isLoading()).toBe(true);

    resolvePromise([]);
    await waitFor(() => vm.isReady());

    expect(vm.isReady()).toBe(true);
  });

  it('retries loading after a failure', async () => {
    port.failure = new Error('Premier echec');
    const vm = createViewModel();

    vm.initialize('group-1');
    await waitFor(() => vm.hasLoadError());
    expect(vm.hasLoadError()).toBe(true);

    port.failure = null;
    port.result = [];
    vm.retry();
    await waitFor(() => vm.isReady());

    expect(vm.isReady()).toBe(true);
    expect(port.requestedGroupIds).toEqual(['group-1', 'group-1']);
  });

  const createViewModel = (): ExpenseListWidgetViewModel => {
    const viewModel = new ExpenseListWidgetViewModel(port, injector);
    TestBed.tick();

    return viewModel;
  };
});

class StubExpenseListPort extends ExpenseListPort {
  result: readonly ExpenseSummary[] = [expense()];
  failure: Error | null = null;
  resultPromise: Promise<readonly ExpenseSummary[]> | null = null;
  requestedGroupIds: string[] = [];

  override async listByGroup(groupId: string): Promise<readonly ExpenseSummary[]> {
    this.requestedGroupIds.push(groupId);
    if (this.failure) throw this.failure;
    if (this.resultPromise) return this.resultPromise;
    return this.result;
  }
}

const expense = (overrides: Partial<ExpenseSummary> = {}): ExpenseSummary => ({
  id: 'e1',
  title: 'Courses',
  createdBy: 'alice@example.com',
  totalAmountInCents: 1500,
  createdAt: new Date('2026-06-01T10:00:00Z'),
  status: 'ACCEPTED',
  ...overrides,
});

const waitFor = async (condition: () => boolean): Promise<void> => {
  for (let attempt = 0; attempt < 50; attempt += 1) {
    if (condition()) return;
    await new Promise((resolve) => setTimeout(resolve));
  }

  throw new Error('La condition attendue n a pas ete atteinte.');
};
