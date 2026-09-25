import { computed, Injectable, Injector, signal } from '@angular/core';
import { injectMutation, QueryClient } from '@tanstack/angular-query-experimental';

import { describeError } from '../../../application/shared/describe-error';
import {
  ExpenseSupportingDocumentDeletionPort,
  type DeleteExpenseSupportingDocumentCommand,
} from '../../../application/supporting-document/expense-supporting-document-deletion.port';

@Injectable()
export class SupportingDocumentHistoryViewModel {
  private readonly deletionState = signal<DeletionState>({ status: 'idle' });
  private readonly deletionMutation;

  readonly isDeleting = computed(() => this.deletionState().status === 'pending');
  readonly isDeleted = computed(() => this.deletionState().status === 'success');
  readonly hasDeletionError = computed(() => this.deletionState().status === 'error');
  readonly deletionErrorMessage = computed(() => {
    const state = this.deletionState();

    return state.status === 'error'
      ? describeError(state.error, 'Le justificatif n a pas pu etre retire.')
      : null;
  });

  constructor(
    private readonly deletionPort: ExpenseSupportingDocumentDeletionPort,
    private readonly queryClient: QueryClient,
    injector: Injector,
  ) {
    this.deletionMutation = injectMutation<void, Error, DeleteExpenseSupportingDocumentCommand>(
      () => ({
        mutationFn: (command) => this.deletionPort.delete(command),
        onSuccess: (_, command) =>
          this.queryClient.invalidateQueries({
            queryKey: [
              'groups',
              command.groupId,
              'expenses',
              command.expenseId,
              'supporting-documents',
            ],
          }),
      }),
      { injector },
    );
  }

  delete(command: DeleteExpenseSupportingDocumentCommand): Promise<void> {
    this.deletionState.set({ status: 'pending' });

    return this.deletionMutation
      .mutateAsync(command)
      .then(() => this.markDeleted(command.sourceUploadIntent))
      .catch((error: unknown) => this.markDeletionFailed(error));
  }

  wasDeleted(sourceUploadIntent: string): boolean {
    const state = this.deletionState();
    return state.status === 'success' && state.sourceUploadIntent === sourceUploadIntent;
  }

  private markDeleted(sourceUploadIntent: string): void {
    this.deletionState.set({ status: 'success', sourceUploadIntent });
  }

  private markDeletionFailed(error: unknown): never {
    this.deletionState.set({ status: 'error', error });
    throw error;
  }
}

type DeletionState =
  | { readonly status: 'idle' }
  | { readonly status: 'pending' }
  | { readonly status: 'success'; readonly sourceUploadIntent: string }
  | { readonly status: 'error'; readonly error: unknown };
