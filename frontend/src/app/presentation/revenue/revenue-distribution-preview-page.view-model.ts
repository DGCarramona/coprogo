import { computed, Injectable, Injector, signal } from '@angular/core';
import { FieldTree, TreeValidationResult, form, required, validate } from '@angular/forms/signals';
import { ActivatedRoute } from '@angular/router';
import { injectMutation } from '@tanstack/angular-query-experimental';

import {
  RevenueDistributionPreviewPort,
  type RevenueDistributionPreviewQuery,
} from '../../application/revenue/revenue-distribution-preview.port';
import { describeError } from '../../application/shared/describe-error';
import type { RevenueDistributionPreview } from '../../domain/revenue/revenue-distribution-preview';
import { formatDate, formatMoneyFromCents } from '../../shared/format/financial-format';
import { parseAmountInCents } from '../../shared/format/parse-amount-in-cents';
import { parseLocalDate } from '../../shared/format/local-date';

interface RevenueDistributionPreviewFormModel {
  amountInEuros: string;
  effectiveDate: string;
}

export interface RevenueDistributionPreviewViewItem {
  readonly effectiveDate: string;
  readonly totalAmount: string;
  readonly allocations: readonly RevenueDistributionAllocationViewItem[];
}

export interface RevenueDistributionAllocationViewItem {
  readonly member: string;
  readonly amount: string;
}

@Injectable()
export class RevenueDistributionPreviewPageViewModel {
  readonly groupId: string | null;
  readonly routeErrorMessage: string | null;
  private readonly formModel = signal<RevenueDistributionPreviewFormModel>(emptyFormModel());
  readonly form: FieldTree<RevenueDistributionPreviewFormModel>;

  private readonly previewMutation;
  readonly preview = computed<RevenueDistributionPreviewViewItem | null>(() => {
    const preview = this.previewMutation.data();
    return preview === undefined ? null : toViewItem(preview);
  });
  readonly isPreviewing = computed(() => this.form().submitting());
  readonly hasPreviewError = computed(() => this.previewMutation.isError());
  readonly previewErrorMessage = computed(() => {
    const error = this.previewMutation.error();
    return error === null
      ? null
      : describeError(error, 'La repartition des revenus n a pas pu etre calculee.');
  });

  constructor(
    route: ActivatedRoute,
    previewPort: RevenueDistributionPreviewPort,
    injector: Injector,
  ) {
    this.groupId = route.snapshot.paramMap.get('groupId');
    this.routeErrorMessage = this.groupId === null ? 'Aucun groupe n’est sélectionné.' : null;
    this.previewMutation = injectMutation<
      RevenueDistributionPreview,
      Error,
      RevenueDistributionPreviewQuery
    >(() => ({ mutationFn: (query) => previewPort.preview(query) }), { injector });
    this.form = form(
      this.formModel,
      (preview) => {
        validate(preview.amountInEuros, ({ value }) =>
          parseAmountInCents(value()) === null
            ? {
                kind: 'amount',
                message: 'Indiquez un montant positif avec deux decimales au plus.',
              }
            : undefined,
        );
        required(preview.effectiveDate, { message: 'Indiquez la date de repartition.' });
        validate(preview.effectiveDate, ({ value }) =>
          parseLocalDate(value()) === null
            ? { kind: 'effective-date', message: 'Indiquez une date valide.' }
            : undefined,
        );
      },
      {
        injector,
        submission: {
          action: async (submittedForm): Promise<TreeValidationResult> => {
            const value = submittedForm().value();
            const amountInCents = parseAmountInCents(value.amountInEuros);
            const effectiveDate = parseLocalDate(value.effectiveDate);
            if (this.groupId === null || amountInCents === null || effectiveDate === null) {
              return {
                kind: 'revenue-distribution-preview',
                message: 'Completez le montant et la date de repartition.',
              };
            }

            try {
              await this.previewMutation.mutateAsync({
                groupId: this.groupId,
                amountInCents,
                effectiveDate,
              });
              return undefined;
            } catch {
              return {
                kind: 'revenue-distribution-preview',
                message: 'La repartition des revenus n a pas pu etre calculee.',
              };
            }
          },
        },
      },
    );
  }
}

const emptyFormModel = (): RevenueDistributionPreviewFormModel => ({
  amountInEuros: '',
  effectiveDate: '',
});

const toViewItem = (preview: RevenueDistributionPreview): RevenueDistributionPreviewViewItem => ({
  effectiveDate: formatDate(preview.effectiveDate),
  totalAmount: formatMoneyFromCents(preview.totalAmountInCents),
  allocations: preview.allocations.map((allocation) => ({
    member: allocation.member,
    amount: formatMoneyFromCents(allocation.amountInCents),
  })),
});
