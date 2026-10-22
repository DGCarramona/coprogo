import { Injector } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { submit } from '@angular/forms/signals';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';

import {
  CashPoolWithdrawalPort,
  InsufficientCashPoolBalanceError,
  type CashPoolWithdrawalCommand,
} from '../../../application/ledger/cash-pool-withdrawal.port';
import { CashPoolWithdrawalWidgetViewModel } from './cash-pool-withdrawal-widget.view-model';

describe('CashPoolWithdrawalWidgetViewModel', () => {
  let port: StubCashPoolWithdrawalPort;
  let injector: Injector;

  beforeEach(() => {
    port = new StubCashPoolWithdrawalPort();
    TestBed.configureTestingModule({ providers: [provideTanStackQuery(new QueryClient())] });
    injector = TestBed.inject(Injector);
  });

  describe('initialize', () => {
    it('starts with an invalid empty form for the selected group', () => {
      const viewModel = createViewModel();

      viewModel.initialize('group-1');

      expect(viewModel.form().value()).toEqual({ amountInEuros: '', withdrawnAt: '' });
      expect(viewModel.form().invalid()).toBe(true);
    });
  });

  describe('form submission', () => {
    it('records the exact withdrawal amount and instant', async () => {
      const viewModel = createViewModel();
      viewModel.initialize('group-1');
      fillForm(viewModel, validValue());

      await expect(submit(viewModel.form)).resolves.toBe(true);
      await waitFor(() => viewModel.isWithdrawn());

      expect(port.commands).toEqual([
        {
          groupId: 'group-1',
          amountInCents: 2550,
          withdrawnAt: new Date('2026-10-03T14:30'),
        },
      ]);
      expect(viewModel.form().value()).toEqual({ amountInEuros: '', withdrawnAt: '' });
    });

    it.each([
      { name: 'an invalid amount', value: validValue({ amountInEuros: '25,555' }) },
      { name: 'a zero amount', value: validValue({ amountInEuros: '0' }) },
      { name: 'a missing withdrawal date', value: validValue({ withdrawnAt: '' }) },
      { name: 'an invalid withdrawal date', value: validValue({ withdrawnAt: 'date-invalide' }) },
    ])('rejects $name', async ({ value }) => {
      const viewModel = createViewModel();
      viewModel.initialize('group-1');
      fillForm(viewModel, value);

      await expect(submit(viewModel.form)).resolves.toBe(false);

      expect(port.commands).toEqual([]);
    });

    it('keeps the entered values and explains an insufficient balance', async () => {
      port.failure = new InsufficientCashPoolBalanceError();
      const viewModel = createViewModel();
      viewModel.initialize('group-1');
      const value = validValue();
      fillForm(viewModel, value);

      await expect(submit(viewModel.form)).resolves.toBe(false);
      await waitFor(() => viewModel.hasWithdrawalError());

      expect(viewModel.form().value()).toEqual(value);
      expect(viewModel.withdrawalErrorMessage()).toBe(
        'Le montant dépasse ce qui est disponible dans la caisse commune.',
      );
    });
  });

  const createViewModel = (): CashPoolWithdrawalWidgetViewModel => {
    const viewModel = new CashPoolWithdrawalWidgetViewModel(port, injector);
    TestBed.tick();
    return viewModel;
  };
});

interface FormValue {
  readonly amountInEuros: string;
  readonly withdrawnAt: string;
}

const validValue = (overrides: Partial<FormValue> = {}): FormValue => ({
  amountInEuros: '25,50',
  withdrawnAt: '2026-10-03T14:30',
  ...overrides,
});

const fillForm = (viewModel: CashPoolWithdrawalWidgetViewModel, value: FormValue): void => {
  viewModel.form.amountInEuros().value.set(value.amountInEuros);
  viewModel.form.withdrawnAt().value.set(value.withdrawnAt);
};

class StubCashPoolWithdrawalPort extends CashPoolWithdrawalPort {
  readonly commands: CashPoolWithdrawalCommand[] = [];
  failure: Error | null = null;

  override async withdraw(command: CashPoolWithdrawalCommand): Promise<void> {
    this.commands.push(command);
    if (this.failure !== null) throw this.failure;
  }
}

const waitFor = async (condition: () => boolean): Promise<void> => {
  for (let attempt = 0; attempt < 50; attempt += 1) {
    TestBed.tick();
    if (condition()) return;
    await new Promise((resolve) => setTimeout(resolve));
  }
  throw new Error('La condition attendue n a pas ete atteinte.');
};
