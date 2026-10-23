import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';

import { CashPoolHistoryPort } from '../../../application/ledger/cash-pool-history.port';
import type { CashPoolHistory } from '../../../domain/ledger/cash-pool-history';
import { CashPoolHistoryWidgetComponent } from './cash-pool-history-widget.component';

describe('CashPoolHistoryWidgetComponent', () => {
  it('shows each income distribution and withdrawal consequence', async () => {
    const port = new StubCashPoolHistoryPort();
    const { fixture, host } = createFixture(port);

    await waitFor(
      fixture,
      () => host.querySelectorAll('li.cash-pool-history__operation').length === 2,
    );

    expect(port.groupIds).toEqual(['group-1']);
    expect(host.textContent).toContain('Revenu ajouté');
    expect(host.textContent).toContain('alice@example.com : 60,00 €');
    expect(host.textContent).toContain('bob@example.com : 40,00 €');
    expect(host.textContent).toContain('Retrait par alice@example.com');
    expect(host.textContent).toContain('60,00 € pris sur sa part');
    expect(host.textContent).toContain('10,00 € ont réduit ce que les autres membres lui doivent');
  });

  it('explains when no cash-pool operation has been recorded', async () => {
    const port = new StubCashPoolHistoryPort();
    port.result = { groupId: 'group-1', operations: [] };
    const { fixture, host } = createFixture(port);

    await waitFor(fixture, () => host.textContent?.includes('Aucune opération') === true);

    expect(host.textContent).toContain('Aucune opération de caisse commune enregistrée.');
  });

  it('shows that the cash-pool history is loading', async () => {
    const port = new StubCashPoolHistoryPort();
    port.pauseNextRequest();
    const { fixture, host } = createFixture(port);

    await waitFor(fixture, () => host.textContent?.includes('Lecture des opérations...') === true);

    expect(host.querySelector('mat-progress-bar')).not.toBeNull();
    port.resumeRequest();
    await waitFor(
      fixture,
      () => host.querySelectorAll('li.cash-pool-history__operation').length === 2,
    );
  });

  it('shows a loading failure and lets the user retry it', async () => {
    const port = new StubCashPoolHistoryPort();
    port.failure = new Error('Historique indisponible');
    const { fixture, host } = createFixture(port);

    await waitFor(fixture, () => host.querySelector('[role="alert"]') !== null);
    expect(host.textContent).toContain('Historique indisponible');

    port.failure = null;
    requiredButton(host, 'button').click();
    await waitFor(
      fixture,
      () => host.querySelectorAll('li.cash-pool-history__operation').length === 2,
    );

    expect(port.groupIds).toEqual(['group-1', 'group-1']);
  });
});

const createFixture = (
  port: StubCashPoolHistoryPort,
): { fixture: ComponentFixture<CashPoolHistoryWidgetComponent>; host: HTMLElement } => {
  TestBed.configureTestingModule({
    imports: [CashPoolHistoryWidgetComponent],
    providers: [
      provideTanStackQuery(new QueryClient({ defaultOptions: { queries: { retry: false } } })),
      { provide: CashPoolHistoryPort, useValue: port },
    ],
  });
  const fixture = TestBed.createComponent(CashPoolHistoryWidgetComponent);
  fixture.componentRef.setInput('groupId', 'group-1');
  fixture.detectChanges();
  return { fixture, host: fixture.nativeElement as HTMLElement };
};

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
  private resumePendingRequest: (() => void) | null = null;
  private shouldPauseNextRequest = false;

  override async get(groupId: string): Promise<CashPoolHistory> {
    this.groupIds.push(groupId);
    if (this.failure !== null) throw this.failure;
    if (this.shouldPauseNextRequest) {
      this.shouldPauseNextRequest = false;
      await new Promise<void>((resolve) => {
        this.resumePendingRequest = resolve;
      });
    }
    return this.result;
  }

  pauseNextRequest(): void {
    this.shouldPauseNextRequest = true;
  }

  resumeRequest(): void {
    if (this.resumePendingRequest === null) throw new Error('Aucune requête en attente.');
    this.resumePendingRequest();
    this.resumePendingRequest = null;
  }
}

const requiredButton = (host: HTMLElement, selector: string): HTMLButtonElement => {
  const button = host.querySelector<HTMLButtonElement>(selector);
  if (button === null) throw new Error(`Bouton absent: ${selector}`);
  return button;
};

const waitFor = async (
  fixture: ComponentFixture<CashPoolHistoryWidgetComponent>,
  condition: () => boolean,
): Promise<void> => {
  for (let attempt = 0; attempt < 50; attempt += 1) {
    TestBed.tick();
    fixture.detectChanges();
    if (condition()) return;
    await new Promise((resolve) => setTimeout(resolve));
  }
  throw new Error('La condition attendue n’a pas été atteinte.');
};
