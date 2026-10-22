import { Injector } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';

import { OwnershipShareTimelinePort } from '../../application/revenue/ownership-share-timeline.port';
import type { OwnershipShareTimeline } from '../../domain/revenue/ownership-share-timeline';
import { OwnershipShareTimelinePageViewModel } from './ownership-share-timeline-page.view-model';

describe('OwnershipShareTimelinePageViewModel', () => {
  let port: StubOwnershipShareTimelinePort;
  let injector: Injector;

  beforeEach(() => {
    port = new StubOwnershipShareTimelinePort();
    TestBed.configureTestingModule({
      providers: [
        provideTanStackQuery(new QueryClient({ defaultOptions: { queries: { retry: false } } })),
      ],
    });
    injector = TestBed.inject(Injector);
  });

  describe('timeline', () => {
    it('loads and presents the ownership-share history selected by the route', async () => {
      const viewModel = createViewModel(routeWith('group-1'));
      await waitFor(() => viewModel.isReady());

      expect(port.requests).toEqual(['group-1']);
      expect(viewModel.changes()).toEqual([
        {
          id: 'change-1',
          effectiveDate: expect.stringContaining('2026'),
          recordedBy: 'alice@example.com',
          recordedAt: expect.stringContaining('2025'),
          shares: [
            { member: 'alice@example.com', percentage: '60 %' },
            { member: 'bob@example.com', percentage: '40 %' },
          ],
        },
      ]);
    });

    it('reports a missing group without querying', () => {
      const viewModel = createViewModel(routeWith(null));

      expect(viewModel.hasLoadError()).toBe(true);
      expect(viewModel.errorMessage()).toBe('Aucun groupe n’est sélectionné.');
      expect(port.requests).toEqual([]);
    });

    it('exposes a loading failure and retries it', async () => {
      port.failure = new Error('Historique indisponible');
      const viewModel = createViewModel(routeWith('group-1'));
      await waitFor(() => viewModel.hasLoadError());

      expect(viewModel.errorMessage()).toBe('Historique indisponible');

      port.failure = null;
      viewModel.retry();
      await waitFor(() => viewModel.isReady());

      expect(port.requests).toEqual(['group-1', 'group-1']);
    });
  });

  const createViewModel = (route: ActivatedRoute) =>
    new OwnershipShareTimelinePageViewModel(route, port, injector);
});

class StubOwnershipShareTimelinePort extends OwnershipShareTimelinePort {
  readonly requests: string[] = [];
  failure: Error | null = null;

  override async getByGroup(groupId: string): Promise<OwnershipShareTimeline> {
    this.requests.push(groupId);
    if (this.failure !== null) throw this.failure;
    return {
      groupId,
      changes: [
        {
          id: 'change-1',
          effectiveDate: new Date(2026, 0, 1),
          recordedBy: 'alice@example.com',
          recordedAt: new Date('2025-12-15T10:00:00Z'),
          shares: [
            { member: 'alice@example.com', percentage: 60 },
            { member: 'bob@example.com', percentage: 40 },
          ],
        },
      ],
    };
  }
}

const routeWith = (groupId: string | null): ActivatedRoute =>
  ({
    snapshot: {
      paramMap: convertToParamMap(groupId === null ? {} : { groupId }),
    },
  }) as ActivatedRoute;

const waitFor = async (condition: () => boolean): Promise<void> => {
  for (let attempt = 0; attempt < 50; attempt += 1) {
    TestBed.tick();
    if (condition()) return;
    await new Promise((resolve) => setTimeout(resolve));
  }
  throw new Error('La condition attendue n a pas ete atteinte.');
};
