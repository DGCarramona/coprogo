import { provideLocalization } from '../../i18n/provide-localization';
import { TestBed } from '@angular/core/testing';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';

import { MemberBalanceImpactHistoryPort } from '../../../application/ledger/member-balance-impact-history.port';
import type { MemberBalanceImpactHistory } from '../../../domain/ledger/member-balance-impact-history';
import { MemberBalanceImpactHistoryWidgetComponent } from './member-balance-impact-history-widget.component';

describe('MemberBalanceImpactHistoryWidgetComponent', () => {
  let port: StubMemberBalanceImpactHistoryPort;

  beforeEach(() => {
    port = new StubMemberBalanceImpactHistoryPort();
    TestBed.configureTestingModule({
      providers: [
        provideLocalization(),
        provideTanStackQuery(new QueryClient({ defaultOptions: { queries: { retry: false } } })),
        { provide: MemberBalanceImpactHistoryPort, useValue: port },
      ],
    });
  });

  it('loads and renders the explanation only after the user asks for it', async () => {
    const fixture = TestBed.createComponent(MemberBalanceImpactHistoryWidgetComponent);
    fixture.componentRef.setInput('groupId', 'group-1');
    fixture.componentRef.setInput('member', 'alice@example.com');
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;

    expect(port.requests).toEqual([]);
    host.querySelector<HTMLButtonElement>('button')?.click();
    await waitFor(() => host.textContent?.includes('Remboursement effectué'));

    expect(port.requests).toEqual([{ groupId: 'group-1', member: 'alice@example.com' }]);
    expect(host.textContent).toContain('12,50');
  });

  it('renders distinct effects of the same withdrawal without describing an increase as a impact', async () => {
    const fixture = TestBed.createComponent(MemberBalanceImpactHistoryWidgetComponent);
    fixture.componentRef.setInput('groupId', 'group-1');
    fixture.componentRef.setInput('member', 'alice@example.com');
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;

    host.querySelector<HTMLButtonElement>('button')?.click();
    await waitFor(() => host.querySelectorAll('li').length === 4);

    expect(Array.from(host.querySelectorAll('li'), (item) => item.textContent?.trim())).toEqual([
      expect.stringContaining('Remboursement effectué'),
      expect.stringContaining('Retrait : ce que ce membre doit a diminué'),
      expect.stringContaining('Retrait : ce que ce membre doit a augmenté'),
      expect.stringContaining('Retrait : ce qu’on doit à ce membre a diminué'),
    ]);
  });
});

class StubMemberBalanceImpactHistoryPort extends MemberBalanceImpactHistoryPort {
  readonly requests: { groupId: string; member: string }[] = [];

  override async get(groupId: string, member: string): Promise<MemberBalanceImpactHistory> {
    this.requests.push({ groupId, member });
    return {
      groupId,
      member,
      impacts: [
        {
          type: 'DIRECT_REIMBURSEMENT',
          effect: 'DEBT_REDUCTION',
          operationId: 'reimbursement-1',
          amountInCents: 1250,
          occurredAt: new Date('2026-04-03T10:00:00Z'),
        },
        ...(['DEBT_REDUCTION', 'DEBT_INCREASE', 'CREDIT_REDUCTION'] as const).map((effect) => ({
          type: 'CASH_POOL_WITHDRAWAL' as const,
          effect,
          operationId: 'withdrawal-1',
          amountInCents: 300,
          occurredAt: new Date('2026-04-03T10:00:00Z'),
        })),
      ],
    };
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
