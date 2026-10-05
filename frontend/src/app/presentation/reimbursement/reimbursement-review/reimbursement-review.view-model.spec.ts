import { Injector } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';

import { ReimbursementReviewPort } from '../../../application/reimbursement/reimbursement-review.port';
import { ReimbursementReviewViewModel } from './reimbursement-review.view-model';

describe('ReimbursementReviewViewModel', () => {
  let queryClient: QueryClient;
  let port: StubReimbursementReviewPort;
  let injector: Injector;

  beforeEach(() => {
    queryClient = new QueryClient();
    port = new StubReimbursementReviewPort();
    TestBed.configureTestingModule({ providers: [provideTanStackQuery(queryClient)] });
    injector = TestBed.inject(Injector);
  });

  describe('accept', () => {
    it('accepts the reimbursement and refreshes its group history', async () => {
      queryClient.setQueryData(['groups', 'group-1', 'reimbursements'], ['cached']);
      const viewModel = createViewModel();

      await viewModel.accept();

      expect(port.acceptances).toEqual([
        { groupId: 'group-1', reimbursementId: 'reimbursement-1' },
      ]);
      expect(
        queryClient.getQueryState(['groups', 'group-1', 'reimbursements'])?.isInvalidated,
      ).toBe(true);
    });
  });

  describe('reject', () => {
    it.each([
      {
        name: 'with a trimmed reason',
        enteredReason: '  Montant incorrect  ',
        expectedReason: 'Montant incorrect',
      },
      { name: 'without a reason', enteredReason: '   ', expectedReason: null },
    ])('rejects the reimbursement $name', async ({ enteredReason, expectedReason }) => {
      const viewModel = createViewModel();
      viewModel.rejectionReason.set(enteredReason);

      await viewModel.reject();

      expect(port.rejections).toEqual([
        {
          groupId: 'group-1',
          reimbursementId: 'reimbursement-1',
          reason: expectedReason,
        },
      ]);
    });

    it('keeps the rejection reason and exposes the error when review fails', async () => {
      port.failure = new Error('Service indisponible');
      const viewModel = createViewModel();
      viewModel.rejectionReason.set('Montant incorrect');

      await viewModel.reject();

      expect(viewModel.rejectionReason()).toBe('Montant incorrect');
      expect(viewModel.errorMessage()).toBe('Service indisponible');
    });
  });

  const createViewModel = (): ReimbursementReviewViewModel => {
    const viewModel = new ReimbursementReviewViewModel(port, queryClient, injector);
    viewModel.initialize('group-1', 'reimbursement-1');
    return viewModel;
  };
});

class StubReimbursementReviewPort extends ReimbursementReviewPort {
  readonly acceptances: { readonly groupId: string; readonly reimbursementId: string }[] = [];
  readonly rejections: {
    readonly groupId: string;
    readonly reimbursementId: string;
    readonly reason: string | null;
  }[] = [];
  failure: Error | null = null;

  override async accept(groupId: string, reimbursementId: string): Promise<void> {
    this.acceptances.push({ groupId, reimbursementId });
    if (this.failure) throw this.failure;
  }

  override async reject(
    groupId: string,
    reimbursementId: string,
    reason: string | null,
  ): Promise<void> {
    this.rejections.push({ groupId, reimbursementId, reason });
    if (this.failure) throw this.failure;
  }
}
