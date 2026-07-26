import { Injectable, computed, signal } from '@angular/core';

import { ExpenseListPort } from '../../../application/expense/expense-list.port';
import { describeError } from '../../../application/shared/describe-error';
import { ExpenseResponseDto } from '../../../infrastructure/api/generated';
import { formatMoneyFromCents } from '../../../shared/format/financial-format';

type WidgetStatus = 'idle' | 'loading' | 'ready' | 'failed';

export interface ExpenseViewItem {
  readonly title: string;
  readonly amount: string;
  readonly createdBy: string;
}

@Injectable()
export class ExpenseListWidgetViewModel {
  private readonly statusState = signal<WidgetStatus>('idle');
  private readonly expensesState = signal<readonly ExpenseResponseDto[]>([]);
  private readonly errorMessageState = signal<string | null>(null);

  constructor(private readonly expensePort: ExpenseListPort) {}

  readonly status = this.statusState.asReadonly();
  readonly errorMessage = this.errorMessageState.asReadonly();
  readonly isLoading = computed(() => this.status() === 'loading');
  readonly hasLoadError = computed(() => this.status() === 'failed');
  readonly isReady = computed(() => this.status() === 'ready');
  readonly expenses = computed<readonly ExpenseViewItem[]>(() =>
    this.expensesState().map((expense) => ({
      title: expense.title,
      amount: formatMoneyFromCents(expense.totalAmountCents),
      createdBy: expense.createdBy,
    })),
  );

  async initialize(groupId: string): Promise<void> {
    await this.load(groupId);
  }

  async retry(): Promise<void> {
    await this.load();
  }

  private async load(groupId?: string): Promise<void> {
    this.statusState.set('loading');
    this.errorMessageState.set(null);

    try {
      this.expensesState.set(await this.expensePort.listByGroup(groupId!));
      this.statusState.set('ready');
    } catch (error) {
      this.expensesState.set([]);
      this.statusState.set('failed');
      this.errorMessageState.set(describeError(error, 'Les depenses n ont pas pu etre chargees.'));
    }
  }
}
