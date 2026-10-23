import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';

import { ReimbursementReviewPort } from '../../../application/reimbursement/reimbursement-review.port';
import { ReimbursementReviewComponent } from './reimbursement-review.component';

describe('ReimbursementReviewComponent', () => {
  it('accepts the reimbursement without a rejection reason', async () => {
    const port = new StubReimbursementReviewPort();
    const { fixture, host } = createFixture(port);
    fixture.detectChanges();

    requiredButton(host, 'button[aria-label="Accepter le remboursement"]').click();

    await waitFor(() => port.acceptances.length === 1);
    expect(port.acceptances).toEqual([{ groupId: 'group-1', reimbursementId: 'reimbursement-1' }]);
  });

  it('rejects the reimbursement with the entered reason', async () => {
    const port = new StubReimbursementReviewPort();
    const { fixture, host } = createFixture(port);
    fixture.detectChanges();
    const reason = requiredReason(host);
    reason.value = 'Montant incorrect';
    reason.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    requiredButton(host, 'button[aria-label="Refuser le remboursement"]').click();

    await waitFor(() => port.rejections.length === 1);
    expect(port.rejections).toEqual([
      {
        groupId: 'group-1',
        reimbursementId: 'reimbursement-1',
        reason: 'Montant incorrect',
      },
    ]);
  });
});

const createFixture = (
  port: StubReimbursementReviewPort,
): { fixture: ComponentFixture<ReimbursementReviewComponent>; host: HTMLElement } => {
  TestBed.configureTestingModule({
    imports: [ReimbursementReviewComponent],
    providers: [
      provideTanStackQuery(new QueryClient()),
      { provide: ReimbursementReviewPort, useValue: port },
    ],
  });
  const fixture = TestBed.createComponent(ReimbursementReviewComponent);
  fixture.componentRef.setInput('groupId', 'group-1');
  fixture.componentRef.setInput('reimbursementId', 'reimbursement-1');
  return { fixture, host: fixture.nativeElement as HTMLElement };
};

class StubReimbursementReviewPort extends ReimbursementReviewPort {
  readonly acceptances: { readonly groupId: string; readonly reimbursementId: string }[] = [];
  readonly rejections: {
    readonly groupId: string;
    readonly reimbursementId: string;
    readonly reason: string | null;
  }[] = [];

  override async accept(groupId: string, reimbursementId: string): Promise<void> {
    this.acceptances.push({ groupId, reimbursementId });
  }

  override async reject(
    groupId: string,
    reimbursementId: string,
    reason: string | null,
  ): Promise<void> {
    this.rejections.push({ groupId, reimbursementId, reason });
  }
}

const requiredReason = (host: HTMLElement): HTMLTextAreaElement => {
  const textarea = host.querySelector<HTMLTextAreaElement>('textarea');
  if (textarea === null) throw new Error('Motif de refus absent');
  return textarea;
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
