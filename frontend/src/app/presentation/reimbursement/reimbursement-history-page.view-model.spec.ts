import { Injector } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';

import { ReimbursementListPort } from '../../application/reimbursement/reimbursement-list.port';
import type { ReimbursementSummary } from '../../domain/reimbursement/reimbursement-summary';
import { ReimbursementHistoryPageViewModel } from './reimbursement-history-page.view-model';

describe('ReimbursementHistoryPageViewModel', () => {
  let queryClient: QueryClient;
  let port: StubReimbursementListPort;
  let injector: Injector;

  beforeEach(() => {
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    port = new StubReimbursementListPort();
    TestBed.configureTestingModule({
      providers: [
        provideTanStackQuery(queryClient),
        { provide: ActivatedRoute, useValue: routeWithGroup('group-1') },
      ],
    });
    injector = TestBed.inject(Injector);
  });

  describe('reimbursements', () => {
    it('loads and presents the reimbursements of the route group', async () => {
      const viewModel = createViewModel();

      await waitFor(() => viewModel.isReady());

      expect(port.requestedGroupIds).toEqual(['group-1']);
      expect(viewModel.reimbursements()).toEqual([
        {
          id: 'reimbursement-1',
          paidBy: 'bob@example.com',
          receivedBy: 'alice@example.com',
          amount: '25,00\u00a0€',
          reimbursedAt: '1 oct. 2026',
          awaitsReview: false,
          documents: [
            {
              sourceUploadIntent: 'upload-intent-1',
              fileName: 'recu.pdf',
              uploader: 'bob@example.com',
              attachedAt: '1 oct. 2026',
              downloadUrl: 'https://documents.example.test/recu.pdf',
            },
          ],
        },
      ]);
    });

    it('marks a pending documented reimbursement as awaiting review', async () => {
      port.result = [pendingReimbursement()];
      const viewModel = createViewModel();

      await waitFor(() => viewModel.isReady());

      expect(
        viewModel.reimbursements().map(({ id, awaitsReview }) => ({ id, awaitsReview })),
      ).toEqual([{ id: 'reimbursement-1', awaitsReview: true }]);
    });

    it('exposes an empty history', async () => {
      port.result = [];
      const viewModel = createViewModel();

      await waitFor(() => viewModel.isReady());

      expect(viewModel.reimbursements()).toEqual([]);
    });

    it('exposes a translated load error', async () => {
      port.failure = new Error('Service indisponible');
      const viewModel = createViewModel();

      await waitFor(() => viewModel.hasLoadError());

      expect(viewModel.errorMessage()).toBe('Service indisponible');
    });

    it('fails clearly when the route has no group', () => {
      const viewModel = createViewModel(routeWithGroup(null));

      expect(viewModel.hasLoadError()).toBe(true);
      expect(viewModel.errorMessage()).toBe('Aucun groupe n est selectionne.');
      expect(port.requestedGroupIds).toEqual([]);
    });
  });

  describe('retry', () => {
    it('reloads the history after a failure', async () => {
      port.failure = new Error('Premier echec');
      const viewModel = createViewModel();
      await waitFor(() => viewModel.hasLoadError());

      port.failure = null;
      viewModel.retry();
      await waitFor(() => viewModel.isReady());

      expect(port.requestedGroupIds).toEqual(['group-1', 'group-1']);
    });
  });

  const createViewModel = (
    route: ActivatedRoute = TestBed.inject(ActivatedRoute),
  ): ReimbursementHistoryPageViewModel => {
    const viewModel = new ReimbursementHistoryPageViewModel(route, port, injector);
    TestBed.tick();
    return viewModel;
  };
});

class StubReimbursementListPort extends ReimbursementListPort {
  result: readonly ReimbursementSummary[] = [reimbursement()];
  failure: Error | null = null;
  readonly requestedGroupIds: string[] = [];

  override async listByGroup(groupId: string): Promise<readonly ReimbursementSummary[]> {
    this.requestedGroupIds.push(groupId);
    if (this.failure) throw this.failure;
    return this.result;
  }
}

const reimbursement = (): ReimbursementSummary => ({
  id: 'reimbursement-1',
  paidBy: 'bob@example.com',
  receivedBy: 'alice@example.com',
  amountInCents: 2500,
  reimbursedAt: new Date('2026-10-01T09:00:00.000Z'),
  declaredBy: 'alice@example.com',
  declaredAt: new Date('2026-10-01T10:00:00.000Z'),
  documents: [
    {
      sourceUploadIntent: 'upload-intent-1',
      fileName: 'recu.pdf',
      mediaType: 'application/pdf',
      sizeBytes: 1234,
      uploader: 'bob@example.com',
      attachedAt: new Date('2026-10-01T09:30:00.000Z'),
      downloadTarget: {
        url: 'https://documents.example.test/recu.pdf',
        expiresAt: new Date('2026-10-01T10:30:00.000Z'),
      },
    },
  ],
  status: 'ACCEPTED',
  decidedAt: new Date('2026-10-01T10:00:00.000Z'),
  rejectionReason: null,
});

const pendingReimbursement = (): ReimbursementSummary => ({
  ...reimbursement(),
  status: 'PENDING_REVIEW',
  decidedAt: null,
  rejectionReason: null,
});

const routeWithGroup = (groupId: string | null): ActivatedRoute =>
  ({
    snapshot: { paramMap: convertToParamMap(groupId === null ? {} : { groupId }) },
  }) as ActivatedRoute;

const waitFor = async (condition: () => boolean): Promise<void> => {
  for (let attempt = 0; attempt < 50; attempt += 1) {
    TestBed.tick();
    if (condition()) return;
    await new Promise((resolve) => setTimeout(resolve));
  }

  throw new Error('La condition attendue n a pas ete atteinte.');
};
