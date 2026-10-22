import { Injector } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { submit } from '@angular/forms/signals';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';

import {
  CashPoolIncomeRecordingPort,
  type CashPoolIncomeRecordingCommand,
} from '../../../application/revenue/cash-pool-income-recording.port';
import { CashPoolIncomeWidgetViewModel } from './cash-pool-income-widget.view-model';

describe('CashPoolIncomeWidgetViewModel', () => {
  let port: StubCashPoolIncomeRecordingPort;
  let injector: Injector;
  let queryClient: QueryClient;

  beforeEach(() => {
    port = new StubCashPoolIncomeRecordingPort();
    queryClient = new QueryClient();
    TestBed.configureTestingModule({ providers: [provideTanStackQuery(queryClient)] });
    injector = TestBed.inject(Injector);
  });

  describe('initialize', () => {
    it('starts with an invalid empty form for the selected group', () => {
      const viewModel = createViewModel();

      viewModel.initialize('group-1');

      expect(viewModel.form().value()).toEqual({
        amountInEuros: '',
        receivedAt: '',
        effectiveDate: '',
      });
      expect(viewModel.form().invalid()).toBe(true);
    });
  });

  describe('form submission', () => {
    it('records the received income with the date selecting the effective shares', async () => {
      const viewModel = createViewModel();
      viewModel.initialize('group-1');
      fillForm(viewModel, validValue());
      queryClient.setQueryData(['groups', 'group-1', 'cash-pool', 'history'], { existing: true });

      await expect(submit(viewModel.form)).resolves.toBe(true);
      await waitFor(() => viewModel.isRecorded());

      expect(port.commands).toEqual([
        {
          groupId: 'group-1',
          amountInCents: 125050,
          receivedAt: new Date('2026-10-01T09:30'),
          effectiveDate: new Date(2026, 8, 1),
        },
      ]);
      expect(viewModel.form().value()).toEqual({
        amountInEuros: '',
        receivedAt: '',
        effectiveDate: '',
      });
      expect(
        queryClient.getQueryState(['groups', 'group-1', 'cash-pool', 'history'])?.isInvalidated,
      ).toBe(true);
    });

    it.each([
      { name: 'an invalid amount', value: validValue({ amountInEuros: '12,345' }) },
      { name: 'a zero amount', value: validValue({ amountInEuros: '0' }) },
      { name: 'a missing reception date', value: validValue({ receivedAt: '' }) },
      { name: 'an invalid reception date', value: validValue({ receivedAt: 'date-invalide' }) },
      { name: 'a missing effective date', value: validValue({ effectiveDate: '' }) },
      { name: 'an impossible effective date', value: validValue({ effectiveDate: '2026-02-30' }) },
    ])('rejects $name', async ({ value }) => {
      const viewModel = createViewModel();
      viewModel.initialize('group-1');
      fillForm(viewModel, value);

      await expect(submit(viewModel.form)).resolves.toBe(false);

      expect(port.commands).toEqual([]);
    });

    it('keeps the entered values and exposes an API failure', async () => {
      port.failure = new Error('Aucune quote-part ne s’applique à cette date.');
      const viewModel = createViewModel();
      viewModel.initialize('group-1');
      const value = validValue();
      fillForm(viewModel, value);

      await expect(submit(viewModel.form)).resolves.toBe(false);
      await waitFor(() => viewModel.hasRecordingError());

      expect(viewModel.form().value()).toEqual(value);
      expect(viewModel.recordingErrorMessage()).toBe(
        'Aucune quote-part ne s’applique à cette date.',
      );
    });
  });

  const createViewModel = (): CashPoolIncomeWidgetViewModel => {
    const viewModel = new CashPoolIncomeWidgetViewModel(port, queryClient, injector);
    TestBed.tick();
    return viewModel;
  };
});

interface FormValue {
  readonly amountInEuros: string;
  readonly receivedAt: string;
  readonly effectiveDate: string;
}

const validValue = (overrides: Partial<FormValue> = {}): FormValue => ({
  amountInEuros: '1250,50',
  receivedAt: '2026-10-01T09:30',
  effectiveDate: '2026-09-01',
  ...overrides,
});

const fillForm = (viewModel: CashPoolIncomeWidgetViewModel, value: FormValue): void => {
  viewModel.form.amountInEuros().value.set(value.amountInEuros);
  viewModel.form.receivedAt().value.set(value.receivedAt);
  viewModel.form.effectiveDate().value.set(value.effectiveDate);
};

class StubCashPoolIncomeRecordingPort extends CashPoolIncomeRecordingPort {
  readonly commands: CashPoolIncomeRecordingCommand[] = [];
  failure: Error | null = null;

  override async record(command: CashPoolIncomeRecordingCommand): Promise<void> {
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
