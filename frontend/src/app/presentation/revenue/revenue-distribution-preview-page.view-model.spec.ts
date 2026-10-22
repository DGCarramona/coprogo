import { Injector } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { submit } from '@angular/forms/signals';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';

import {
  RevenueDistributionPreviewPort,
  type RevenueDistributionPreviewQuery,
} from '../../application/revenue/revenue-distribution-preview.port';
import type { RevenueDistributionPreview } from '../../domain/revenue/revenue-distribution-preview';
import { RevenueDistributionPreviewPageViewModel } from './revenue-distribution-preview-page.view-model';

describe('RevenueDistributionPreviewPageViewModel', () => {
  let port: StubRevenueDistributionPreviewPort;
  let injector: Injector;

  beforeEach(() => {
    port = new StubRevenueDistributionPreviewPort();
    TestBed.configureTestingModule({
      providers: [provideTanStackQuery(new QueryClient())],
    });
    injector = TestBed.inject(Injector);
  });

  describe('preview', () => {
    it('calculates and presents the distribution for the entered amount and date', async () => {
      const viewModel = createViewModel(routeWith('group-1'));
      fillForm(viewModel, { amountInEuros: '25,50', effectiveDate: '2026-11-01' });

      await expect(submit(viewModel.form)).resolves.toBe(true);
      await waitFor(() => viewModel.preview() !== null);

      expect(port.queries).toEqual([
        {
          groupId: 'group-1',
          amountInCents: 2550,
          effectiveDate: new Date(2026, 10, 1),
        },
      ]);
      expect(viewModel.preview()).toEqual({
        effectiveDate: expect.stringContaining('2026'),
        totalAmount: '25,50 €',
        allocations: [
          { member: 'alice@example.com', amount: '15,30 €' },
          { member: 'bob@example.com', amount: '10,20 €' },
        ],
      });
    });

    it.each([
      { name: 'an invalid amount', amountInEuros: '25,555', effectiveDate: '2026-11-01' },
      { name: 'a zero amount', amountInEuros: '0', effectiveDate: '2026-11-01' },
      { name: 'a missing date', amountInEuros: '25,50', effectiveDate: '' },
      { name: 'an impossible date', amountInEuros: '25,50', effectiveDate: '2026-02-30' },
    ])('rejects $name', async ({ amountInEuros, effectiveDate }) => {
      const viewModel = createViewModel(routeWith('group-1'));
      fillForm(viewModel, { amountInEuros, effectiveDate });

      await expect(submit(viewModel.form)).resolves.toBe(false);

      expect(port.queries).toEqual([]);
    });

    it('reports a missing group without calling the API', async () => {
      const viewModel = createViewModel(routeWith(null));
      fillForm(viewModel, { amountInEuros: '25,50', effectiveDate: '2026-11-01' });

      await expect(submit(viewModel.form)).resolves.toBe(false);

      expect(viewModel.groupId).toBeNull();
      expect(viewModel.routeErrorMessage).toBe('Aucun groupe n’est sélectionné.');
      expect(port.queries).toEqual([]);
    });

    it('keeps the entered values and exposes an API failure', async () => {
      port.failure = new Error('Aucune quote-part ne s’applique à cette date.');
      const viewModel = createViewModel(routeWith('group-1'));
      const value = { amountInEuros: '25,50', effectiveDate: '2026-11-01' };
      fillForm(viewModel, value);

      await expect(submit(viewModel.form)).resolves.toBe(false);
      await waitFor(() => viewModel.hasPreviewError());

      expect(viewModel.form().value()).toEqual(value);
      expect(viewModel.previewErrorMessage()).toBe('Aucune quote-part ne s’applique à cette date.');
    });
  });

  const createViewModel = (route: ActivatedRoute): RevenueDistributionPreviewPageViewModel => {
    const viewModel = new RevenueDistributionPreviewPageViewModel(route, port, injector);
    TestBed.tick();
    return viewModel;
  };
});

const fillForm = (
  viewModel: RevenueDistributionPreviewPageViewModel,
  value: { readonly amountInEuros: string; readonly effectiveDate: string },
): void => {
  viewModel.form.amountInEuros().value.set(value.amountInEuros);
  viewModel.form.effectiveDate().value.set(value.effectiveDate);
};

class StubRevenueDistributionPreviewPort extends RevenueDistributionPreviewPort {
  readonly queries: RevenueDistributionPreviewQuery[] = [];
  failure: Error | null = null;

  override async preview(
    query: RevenueDistributionPreviewQuery,
  ): Promise<RevenueDistributionPreview> {
    this.queries.push(query);
    if (this.failure !== null) throw this.failure;
    return {
      groupId: query.groupId,
      effectiveDate: query.effectiveDate,
      totalAmountInCents: query.amountInCents,
      allocations: [
        { member: 'alice@example.com', amountInCents: 1530 },
        { member: 'bob@example.com', amountInCents: 1020 },
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
