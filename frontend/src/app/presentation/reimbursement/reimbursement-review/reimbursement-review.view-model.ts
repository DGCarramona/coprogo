import { computed, Injectable, Injector, signal } from '@angular/core';
import { injectMutation, QueryClient } from '@tanstack/angular-query-experimental';

import { ReimbursementReviewPort } from '../../../application/reimbursement/reimbursement-review.port';
import { describeError } from '../../../application/shared/describe-error';

interface ReimbursementReviewTarget {
  readonly groupId: string;
  readonly reimbursementId: string;
}

interface ReimbursementRejection extends ReimbursementReviewTarget {
  readonly reason: string | null;
}

@Injectable()
export class ReimbursementReviewViewModel {
  private readonly targetState = signal<ReimbursementReviewTarget | null>(null);
  private readonly errorMessageState = signal<string | null>(null);
  readonly rejectionReason = signal('');

  private readonly acceptanceMutation;
  private readonly rejectionMutation;
  readonly isReviewing = computed(
    () => this.acceptanceMutation.isPending() || this.rejectionMutation.isPending(),
  );
  readonly errorMessage = this.errorMessageState.asReadonly();

  constructor(
    private readonly reviewPort: ReimbursementReviewPort,
    private readonly queryClient: QueryClient,
    injector: Injector,
  ) {
    this.acceptanceMutation = injectMutation<void, Error, ReimbursementReviewTarget>(
      () => ({
        mutationFn: ({ groupId, reimbursementId }) =>
          this.reviewPort.accept(groupId, reimbursementId),
        onSuccess: (_, target) => this.invalidateHistory(target.groupId),
      }),
      { injector },
    );
    this.rejectionMutation = injectMutation<void, Error, ReimbursementRejection>(
      () => ({
        mutationFn: ({ groupId, reimbursementId, reason }) =>
          this.reviewPort.reject(groupId, reimbursementId, reason),
        onSuccess: (_, target) => this.invalidateHistory(target.groupId),
      }),
      { injector },
    );
  }

  initialize(groupId: string, reimbursementId: string): void {
    this.targetState.set({ groupId, reimbursementId });
    this.rejectionReason.set('');
    this.errorMessageState.set(null);
  }

  async accept(): Promise<void> {
    await this.review((target) => this.acceptanceMutation.mutateAsync(target));
  }

  async reject(): Promise<void> {
    const enteredReason = this.rejectionReason().trim();
    await this.review((target) =>
      this.rejectionMutation.mutateAsync({
        ...target,
        reason: enteredReason.length === 0 ? null : enteredReason,
      }),
    );
  }

  private async review(
    action: (target: ReimbursementReviewTarget) => Promise<void>,
  ): Promise<void> {
    this.errorMessageState.set(null);
    const target = this.targetState();
    if (target === null) {
      this.errorMessageState.set('Le remboursement a verifier est manquant.');
      return;
    }

    try {
      await action(target);
    } catch (error) {
      this.errorMessageState.set(
        describeError(error, 'La decision sur le remboursement n a pas pu etre enregistree.'),
      );
    }
  }

  private invalidateHistory(groupId: string): Promise<void> {
    return this.queryClient.invalidateQueries({
      queryKey: ['groups', groupId, 'reimbursements'],
    });
  }
}
