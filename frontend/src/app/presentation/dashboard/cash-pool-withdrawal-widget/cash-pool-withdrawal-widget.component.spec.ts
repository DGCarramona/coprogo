import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';

import {
  CashPoolWithdrawalPort,
  InsufficientCashPoolBalanceError,
  type CashPoolWithdrawalCommand,
} from '../../../application/ledger/cash-pool-withdrawal.port';
import { CashPoolWithdrawalWidgetComponent } from './cash-pool-withdrawal-widget.component';

describe('CashPoolWithdrawalWidgetComponent', () => {
  it('records the withdrawal and notifies its container after success', async () => {
    const port = new StubCashPoolWithdrawalPort();
    const { fixture, host } = createFixture(port);
    let withdrawnCount = 0;
    fixture.componentInstance.withdrawn.subscribe(() => (withdrawnCount += 1));
    fixture.detectChanges();

    fillValidForm(host, fixture);
    requiredButton(host, 'button[type="submit"]').click();

    await waitFor(() => withdrawnCount === 1);
    expect(port.commands).toEqual([
      {
        groupId: 'group-1',
        amountInCents: 2550,
        withdrawnAt: new Date('2026-10-03T14:30'),
      },
    ]);
  });

  it('keeps submission disabled until the amount and date are valid', () => {
    const { fixture, host } = createFixture(new StubCashPoolWithdrawalPort());
    fixture.detectChanges();

    expect(requiredButton(host, 'button[type="submit"]').disabled).toBe(true);

    setValue(host, fixture, 'input[aria-label="Montant du retrait en euros"]', '25,50');

    expect(requiredButton(host, 'button[type="submit"]').disabled).toBe(true);
  });

  it('explains when the requested amount is unavailable', async () => {
    const port = new StubCashPoolWithdrawalPort();
    port.failure = new InsufficientCashPoolBalanceError();
    const { fixture, host } = createFixture(port);
    fixture.detectChanges();
    fillValidForm(host, fixture);

    requiredButton(host, 'button[type="submit"]').click();
    await waitFor(() => {
      fixture.detectChanges();
      return host.querySelector('[role="alert"]') !== null;
    });

    expect(host.textContent).toContain(
      'Le montant dépasse ce qui est disponible dans la caisse commune.',
    );
  });
});

const createFixture = (
  port: StubCashPoolWithdrawalPort,
): { fixture: ComponentFixture<CashPoolWithdrawalWidgetComponent>; host: HTMLElement } => {
  TestBed.configureTestingModule({
    imports: [CashPoolWithdrawalWidgetComponent],
    providers: [
      provideTanStackQuery(new QueryClient()),
      { provide: CashPoolWithdrawalPort, useValue: port },
    ],
  });
  const fixture = TestBed.createComponent(CashPoolWithdrawalWidgetComponent);
  fixture.componentRef.setInput('groupId', 'group-1');
  return { fixture, host: fixture.nativeElement as HTMLElement };
};

class StubCashPoolWithdrawalPort extends CashPoolWithdrawalPort {
  readonly commands: CashPoolWithdrawalCommand[] = [];
  failure: Error | null = null;

  override async withdraw(command: CashPoolWithdrawalCommand): Promise<void> {
    this.commands.push(command);
    if (this.failure !== null) throw this.failure;
  }
}

const fillValidForm = (
  host: HTMLElement,
  fixture: ComponentFixture<CashPoolWithdrawalWidgetComponent>,
): void => {
  setValue(host, fixture, 'input[aria-label="Montant du retrait en euros"]', '25,50');
  setValue(host, fixture, 'input[aria-label="Date du retrait"]', '2026-10-03T14:30');
};

const setValue = (
  host: HTMLElement,
  fixture: ComponentFixture<CashPoolWithdrawalWidgetComponent>,
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

const waitFor = async (condition: () => boolean): Promise<void> => {
  for (let attempt = 0; attempt < 50; attempt += 1) {
    TestBed.tick();
    if (condition()) return;
    await new Promise((resolve) => setTimeout(resolve));
  }
  throw new Error('La condition attendue n a pas ete atteinte.');
};
