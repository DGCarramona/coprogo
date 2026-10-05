import { Injector } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';

import { UploadSupportingDocument } from '../../../application/supporting-document/upload-supporting-document.use-case';
import {
  Sha256ChecksumPort,
  SignedSupportingDocumentUploaderPort,
  SupportingDocumentUploadControlPlanePort,
  type SupportingDocumentFile,
  type SupportingDocumentUploadTarget,
} from '../../../application/supporting-document/supporting-document-upload.port';
import { SupportingDocumentUploadWidgetViewModel } from './supporting-document-upload-widget.view-model';

describe('SupportingDocumentUploadWidgetViewModel', () => {
  let uploadSupportingDocument: StubUploadSupportingDocument;
  let viewModel: SupportingDocumentUploadWidgetViewModel;

  beforeEach(() => {
    uploadSupportingDocument = new StubUploadSupportingDocument();
    TestBed.configureTestingModule({
      providers: [
        provideTanStackQuery(new QueryClient({ defaultOptions: { queries: { retry: false } } })),
      ],
    });
    viewModel = new SupportingDocumentUploadWidgetViewModel(
      uploadSupportingDocument,
      TestBed.inject(Injector),
    );
    viewModel.initialize('group-1');
  });

  describe('upload', () => {
    it('uploads a selected file for its initialized group', async () => {
      const file: SupportingDocumentFile = {
        fileName: 'facture.pdf',
        mediaType: 'application/pdf',
        bytes: new Uint8Array([1, 2, 3]),
      };

      await expect(viewModel.upload(file)).resolves.toBe('intent-1');

      expect(uploadSupportingDocument.commands).toEqual([
        {
          groupId: 'group-1',
          file,
        },
      ]);
      await waitFor(() => viewModel.isUploaded());
    });

    it('exposes pending state until the upload is confirmed', async () => {
      uploadSupportingDocument.defer();

      const upload = viewModel.upload({
        fileName: 'facture.pdf',
        mediaType: 'application/pdf',
        bytes: new Uint8Array([1, 2, 3]),
      });

      await waitFor(() => viewModel.isUploading());
      expect(viewModel.hasUploadError()).toBe(false);

      uploadSupportingDocument.resolve('intent-1');
      await expect(upload).resolves.toBe('intent-1');
      expect(viewModel.isUploading()).toBe(false);
    });

    it('exposes a plain error when the upload fails', async () => {
      uploadSupportingDocument.failure = new Error('Le fichier n a pas pu etre envoye.');

      await expect(
        viewModel.upload({
          fileName: 'facture.pdf',
          mediaType: 'application/pdf',
          bytes: new Uint8Array([1, 2, 3]),
        }),
      ).rejects.toThrow('Le fichier n a pas pu etre envoye.');

      await waitFor(() => viewModel.hasUploadError());
      expect(viewModel.uploadErrorMessage()).toBe('Le fichier n a pas pu etre envoye.');
    });
  });
});

class StubUploadSupportingDocument extends UploadSupportingDocument {
  readonly commands: { groupId: string; file: SupportingDocumentFile }[] = [];
  failure: Error | null = null;
  private deferred: Promise<string> | null = null;
  private resolveDeferred: ((intentId: string) => void) | null = null;

  constructor() {
    super(
      new NoopChecksumPort(),
      new NoopSupportingDocumentUploadControlPlanePort(),
      new NoopSignedSupportingDocumentUploaderPort(),
    );
  }

  override upload(groupId: string, file: SupportingDocumentFile): Promise<string> {
    this.commands.push({ groupId, file });
    if (this.failure !== null) return Promise.reject(this.failure);
    return this.deferred ?? Promise.resolve('intent-1');
  }

  defer(): void {
    this.deferred = new Promise((resolve) => {
      this.resolveDeferred = resolve;
    });
  }

  resolve(intentId: string): void {
    this.resolveDeferred?.(intentId);
  }
}

class NoopChecksumPort extends Sha256ChecksumPort {
  override checksum(): Promise<string> {
    return Promise.resolve('checksum');
  }
}

class NoopSupportingDocumentUploadControlPlanePort extends SupportingDocumentUploadControlPlanePort {
  override start(): Promise<SupportingDocumentUploadTarget> {
    return Promise.resolve({ intentId: 'intent-1', uploadUrl: '', requiredHeaders: {} });
  }

  override confirm(): Promise<void> {
    return Promise.resolve();
  }
}

class NoopSignedSupportingDocumentUploaderPort extends SignedSupportingDocumentUploaderPort {
  override upload(): Promise<void> {
    return Promise.resolve();
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
