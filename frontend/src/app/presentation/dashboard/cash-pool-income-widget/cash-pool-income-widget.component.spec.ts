import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';

import {
  CashPoolIncomeRecordingPort,
  type CashPoolIncomeRecordingCommand,
} from '../../../application/revenue/cash-pool-income-recording.port';
import { CashPoolIncomeWidgetComponent } from './cash-pool-income-widget.component';

describe('CashPoolIncomeWidgetComponent', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 9, 27, 0, 30));
  });
  afterEach(() => vi.useRealTimers());
  it('records the income and notifies its container after success', async () => {
    const port = new StubCashPoolIncomeRecordingPort();
    const { fixture, host } = createFixture(port);
    let recordedCount = 0;
    fixture.componentInstance.recorded.subscribe(() => (recordedCount += 1));
    fixture.detectChanges();

    setValue(host, fixture, 'input[aria-label="Montant du revenu en euros"]', '1250,50');
    setValue(host, fixture, 'input[aria-label="Date de réception"]', '2026-10-01T09:30');
    setValue(host, fixture, 'input[aria-label="Date des quotes-parts"]', '2026-09-01');
    requiredButton(host, 'button[type="submit"]').click();

    await waitFor(() => recordedCount === 1);
    fixture.detectChanges();
    expect(port.commands).toEqual([
      {
        groupId: 'group-1',
        amountInCents: 125050,
        receivedAt: new Date('2026-10-01T09:30'),
        effectiveDate: new Date(2026, 8, 1),
      },
    ]);
    expect(host.querySelector('[role="status"]')?.textContent).toContain(
      'Le revenu et sa répartition ont été enregistrés.',
    );
  });

  it('prefills today and enables submission after entering only amount and reception date', () => {
    const { fixture, host } = createFixture(new StubCashPoolIncomeRecordingPort());
    fixture.detectChanges();

    expect(requiredButton(host, 'button[type="submit"]').disabled).toBe(true);
    expect(requiredInput(host, 'input[aria-label="Date des quotes-parts"]').value).toBe(
      '2026-10-27',
    );

    setValue(host, fixture, 'input[aria-label="Montant du revenu en euros"]', '1250,50');
    setValue(host, fixture, 'input[aria-label="Date de réception"]', '2026-10-01T09:30');

    expect(requiredButton(host, 'button[type="submit"]').disabled).toBe(false);
  });

  it('explains which date controls the applied ownership shares', () => {
    const { fixture, host } = createFixture(new StubCashPoolIncomeRecordingPort());

    fixture.detectChanges();

    expect(host.textContent).toContain(
      'La date des quotes-parts détermine la répartition appliquée à ce revenu.',
    );
  });

  it('keeps the income editable and lets the member adjust it after recording fails', async () => {
    const port = new StubCashPoolIncomeRecordingPort();
    port.failure = new Error('Aucune quote-part ne s’applique à cette date.');
    const { fixture, host } = createFixture(port);
    fixture.detectChanges();
    fillValidForm(host, fixture);

    requiredButton(host, 'button[type="submit"]').click();
    await waitFor(() => {
      fixture.detectChanges();
      return host.querySelector('[role="alert"]') !== null;
    });

    expect(host.textContent).toContain('Aucune quote-part ne s’applique à cette date.');
    expect(requiredInput(host, 'input[aria-label="Montant du revenu en euros"]').value).toBe(
      '1250,50',
    );
    expect(requiredInput(host, 'input[aria-label="Date de réception"]').value).toBe(
      '2026-10-01T09:30',
    );
    expect(requiredInput(host, 'input[aria-label="Date des quotes-parts"]').value).toBe(
      '2026-09-01',
    );

    port.failure = null;
    setValue(host, fixture, 'input[aria-label="Date des quotes-parts"]', '2026-08-01');
    requiredButton(host, 'button[type="submit"]').click();
    await waitFor(() => {
      fixture.detectChanges();
      return host.querySelector('[role="status"]') !== null;
    });

    expect(port.commands).toHaveLength(2);
  });
});

const createFixture = (
  port: StubCashPoolIncomeRecordingPort,
): { fixture: ComponentFixture<CashPoolIncomeWidgetComponent>; host: HTMLElement } => {
  TestBed.configureTestingModule({
    imports: [CashPoolIncomeWidgetComponent],
    providers: [
      provideTanStackQuery(new QueryClient()),
      { provide: CashPoolIncomeRecordingPort, useValue: port },
    ],
  });
  const fixture = TestBed.createComponent(CashPoolIncomeWidgetComponent);
  fixture.componentRef.setInput('groupId', 'group-1');
  return { fixture, host: fixture.nativeElement as HTMLElement };
};

class StubCashPoolIncomeRecordingPort extends CashPoolIncomeRecordingPort {
  readonly commands: CashPoolIncomeRecordingCommand[] = [];
  failure: Error | null = null;

  override async record(command: CashPoolIncomeRecordingCommand): Promise<void> {
    this.commands.push(command);
    if (this.failure !== null) throw this.failure;
  }
}

const fillValidForm = (
  host: HTMLElement,
  fixture: ComponentFixture<CashPoolIncomeWidgetComponent>,
): void => {
  setValue(host, fixture, 'input[aria-label="Montant du revenu en euros"]', '1250,50');
  setValue(host, fixture, 'input[aria-label="Date de réception"]', '2026-10-01T09:30');
  setValue(host, fixture, 'input[aria-label="Date des quotes-parts"]', '2026-09-01');
};

const setValue = (
  host: HTMLElement,
  fixture: ComponentFixture<CashPoolIncomeWidgetComponent>,
  selector: string,
  value: string,
): void => {
  const input = host.querySelector<HTMLInputElement>(selector);
  if (input === null) throw new Error(`Champ absent: ${selector}`);
  input.value = value;
  input.dispatchEvent(new Event('input'));
  fixture.detectChanges();
};

const requiredButton = (host: HTMLElement, selector: string): HTMLButtonElement => {
  const button = host.querySelector<HTMLButtonElement>(selector);
  if (button === null) throw new Error(`Bouton absent: ${selector}`);
  return button;
};

const requiredInput = (host: HTMLElement, selector: string): HTMLInputElement => {
  const input = host.querySelector<HTMLInputElement>(selector);
  if (input === null) throw new Error(`Champ absent: ${selector}`);
  return input;
};

const waitFor = async (condition: () => boolean): Promise<void> => {
  for (let attempt = 0; attempt < 50; attempt += 1) {
    TestBed.tick();
    if (condition()) return;
    await new Promise((resolve) => setTimeout(resolve));
  }
  throw new Error('La condition attendue n a pas ete atteinte.');
};
