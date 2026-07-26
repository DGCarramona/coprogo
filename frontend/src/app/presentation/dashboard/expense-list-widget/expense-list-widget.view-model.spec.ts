import { ExpenseListWidgetViewModel } from './expense-list-widget.view-model';
import { ExpenseListPort } from '../../../application/expense/expense-list.port';
import { ExpenseResponseDto } from '../../../infrastructure/api/generated';

describe('ExpenseListWidgetViewModel', () => {
  it('loads expenses for the given group', async () => {
    const port = new StubExpenseListPort();
    const vm = new ExpenseListWidgetViewModel(port);

    await vm.initialize('group-1');

    expect(vm.status()).toBe('ready');
    expect(vm.expenses()).toEqual([
      { title: 'Courses', amount: '15,00\u00a0€', createdBy: 'alice@example.com' },
    ]);
  });

  it('exposes empty list when no expenses', async () => {
    const port = new StubExpenseListPort();
    port.result = [];
    const vm = new ExpenseListWidgetViewModel(port);

    await vm.initialize('group-1');

    expect(vm.expenses()).toEqual([]);
  });

  it('exposes a load error', async () => {
    const port = new StubExpenseListPort();
    port.failure = new Error('Erreur reseau');
    const vm = new ExpenseListWidgetViewModel(port);

    await vm.initialize('group-1');

    expect(vm.hasLoadError()).toBe(true);
    expect(vm.errorMessage()).toBe('Erreur reseau');
  });

  it('is loading while fetching', async () => {
    const port = new StubExpenseListPort();
    let resolvePromise!: (value: ExpenseResponseDto[]) => void;
    port.resultPromise = new Promise((resolve) => {
      resolvePromise = resolve;
    });
    const vm = new ExpenseListWidgetViewModel(port);

    const loadPromise = vm.initialize('group-1');

    expect(vm.isLoading()).toBe(true);

    resolvePromise([]);
    await loadPromise;

    expect(vm.isReady()).toBe(true);
  });

  it('retries loading after a failure', async () => {
    const port = new StubExpenseListPort();
    port.failure = new Error('Premier echec');
    const vm = new ExpenseListWidgetViewModel(port);

    await vm.initialize('group-1');
    expect(vm.hasLoadError()).toBe(true);

    port.failure = null;
    port.result = [];
    await vm.retry();

    expect(vm.isReady()).toBe(true);
  });
});

class StubExpenseListPort extends ExpenseListPort {
  result: ExpenseResponseDto[] = [
    {
      id: 'e1',
      title: 'Courses',
      createdBy: 'alice@example.com',
      totalAmountCents: 1500,
      createdAt: '2026-06-01T10:00:00Z',
      status: 'ACCEPTED',
    },
  ];
  failure: Error | null = null;
  resultPromise: Promise<ExpenseResponseDto[]> | null = null;

  override async listByGroup(): Promise<ExpenseResponseDto[]> {
    if (this.failure) throw this.failure;
    if (this.resultPromise) return this.resultPromise;
    return this.result;
  }
}
