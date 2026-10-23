import { TestBed } from '@angular/core/testing';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';

import { MemberDebtReductionHistoryPort } from '../../../application/ledger/member-debt-reduction-history.port';
import type { MemberDebtReductionHistory } from '../../../domain/ledger/member-debt-reduction-history';
import { MemberDebtReductionHistoryWidgetComponent } from './member-debt-reduction-history-widget.component';

describe('MemberDebtReductionHistoryWidgetComponent', () => {
  let port: StubMemberDebtReductionHistoryPort;

  beforeEach(() => {
    port = new StubMemberDebtReductionHistoryPort();
    TestBed.configureTestingModule({
      providers: [
        provideTanStackQuery(new QueryClient({ defaultOptions: { queries: { retry: false } } })),
        { provide: MemberDebtReductionHistoryPort, useValue: port },
      ],
    });
  });

  it('loads and renders the explanation only after the user asks for it', async () => {
    const fixture = TestBed.createComponent(MemberDebtReductionHistoryWidgetComponent);
    fixture.componentRef.setInput('groupId', 'group-1');
    fixture.componentRef.setInput('member', 'alice@example.com');
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;

    expect(port.requests).toEqual([]);
    host.querySelector<HTMLButtonElement>('button')?.click();
    await waitFor(() => host.textContent?.includes('Remboursement effectué') === true);

    expect(port.requests).toEqual([{ groupId: 'group-1', member: 'alice@example.com' }]);
    expect(host.textContent).toContain('12,50');
  });
});

class StubMemberDebtReductionHistoryPort extends MemberDebtReductionHistoryPort {
  readonly requests: { groupId: string; member: string }[] = [];

  override async get(groupId: string, member: string): Promise<MemberDebtReductionHistory> {
    this.requests.push({ groupId, member });
    return {
      groupId,
      member,
      reductions: [
        {
          type: 'DIRECT_REIMBURSEMENT',
          operationId: 'reimbursement-1',
          amountInCents: 1250,
          occurredAt: new Date('2026-04-03T10:00:00Z'),
        },
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
