import { computed, Injectable, Injector, signal } from '@angular/core';
import { injectMutation } from '@tanstack/angular-query-experimental';

import { describeError } from '../../../application/shared/describe-error';
import type { SupportingDocumentFile } from '../../../application/supporting-document/supporting-document-upload.port';
import { UploadSupportingDocument } from '../../../application/supporting-document/upload-supporting-document.use-case';

@Injectable()
export class SupportingDocumentUploadWidgetViewModel {
  private readonly groupIdState = signal<string | null>(null);
  private readonly uploadState = signal<UploadState>({ status: 'idle' });
  private readonly uploadMutation;

  readonly isUploading = computed(() => this.uploadState().status === 'pending');
  readonly isUploaded = computed(() => this.uploadState().status === 'success');
  readonly hasUploadError = computed(() => this.uploadState().status === 'error');
  readonly uploadErrorMessage = computed(() => {
    const state = this.uploadState();

    return state.status === 'error'
      ? describeError(state.error, 'Le fichier n a pas pu etre envoye.')
      : null;
  });

  constructor(
    private readonly uploadSupportingDocument: UploadSupportingDocument,
    injector: Injector,
  ) {
    this.uploadMutation = injectMutation<string, Error, SupportingDocumentFile>(
      () => ({
        mutationFn: (file) => {
          const groupId = this.groupIdState();
          if (groupId === null) {
            throw new Error('Un groupe doit etre initialise avant d envoyer un justificatif.');
          }

          return this.uploadSupportingDocument.upload(groupId, file);
        },
      }),
      { injector },
    );
  }

  initialize(groupId: string): void {
    this.groupIdState.set(groupId);
    this.uploadState.set({ status: 'idle' });
    this.uploadMutation.reset();
  }

  async upload(file: SupportingDocumentFile): Promise<string> {
    this.uploadState.set({ status: 'pending' });

    return await this.uploadMutation
      .mutateAsync(file)
      .then((intentId) => this.markUploaded(intentId))
      .catch((error: unknown) => this.markUploadFailed(error));
  }

  private markUploaded(intentId: string): string {
    this.uploadState.set({ status: 'success' });
    return intentId;
  }

  private markUploadFailed(error: unknown): never {
    this.uploadState.set({ status: 'error', error });
    throw error;
  }
}

type UploadState =
  | { readonly status: 'idle' }
  | { readonly status: 'pending' }
  | { readonly status: 'success' }
  | { readonly status: 'error'; readonly error: unknown };
