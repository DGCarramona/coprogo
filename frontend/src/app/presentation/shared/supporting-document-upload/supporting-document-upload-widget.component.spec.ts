import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';

import { UploadSupportingDocument } from '../../../application/supporting-document/upload-supporting-document.use-case';
import type { SupportingDocumentFile } from '../../../application/supporting-document/supporting-document-upload.port';
import { SupportingDocumentUploadWidgetComponent } from './supporting-document-upload-widget.component';

describe('SupportingDocumentUploadWidgetComponent', () => {
  describe('file selection', () => {
    it('maps a selected file, confirms it and emits its confirmed identifier', async () => {
      const upload = new StubUploadSupportingDocument();
      const { fixture, host } = createFixture(upload);
      const confirmed: unknown[] = [];
      fixture.componentInstance.confirmed.subscribe((document) => confirmed.push(document));
      const input = requiredInput(host, 'input[type="file"]');
      const file = new File([new Uint8Array([1, 2, 3])], 'facture.pdf', {
        type: 'application/pdf',
      });

      selectFile(input, file);

      await waitFor(() => confirmed.length === 1);
      expect(upload.commands).toEqual([
        {
          groupId: 'group-1',
          file: {
            fileName: 'facture.pdf',
            mediaType: 'application/pdf',
            bytes: new Uint8Array([1, 2, 3]),
          },
        },
      ]);
      expect(confirmed).toEqual([{ intentId: 'intent-1', fileName: 'facture.pdf' }]);
      fixture.detectChanges();
      expect(host.querySelector('[role="status"]')?.textContent).toContain('ajouté');
    });

    it('uses a generic media type when the browser does not provide one', async () => {
      const upload = new StubUploadSupportingDocument();
      const { host } = createFixture(upload);
      const input = requiredInput(host, 'input[type="file"]');

      selectFile(input, new File([new Uint8Array([7])], 'recu', { type: '' }));

      await waitFor(() => upload.commands.length === 1);
      expect(upload.commands).toEqual([
        {
          groupId: 'group-1',
          file: {
            fileName: 'recu',
            mediaType: 'application/octet-stream',
            bytes: new Uint8Array([7]),
          },
        },
      ]);
    });

    it('disables file selection while sending the file', async () => {
      const upload = new StubUploadSupportingDocument();
      upload.defer();
      const { fixture, host } = createFixture(upload);
      const input = requiredInput(host, 'input[type="file"]');

      selectFile(input, new File([new Uint8Array([1])], 'facture.pdf'));

      await waitFor(() => fixture.componentInstance.viewModel.isUploading());
      fixture.detectChanges();
      expect(input.disabled).toBe(true);
      expect(host.querySelector('[role="status"]')?.textContent).toContain('Envoi');

      upload.resolve('intent-1');
    });

    it('shows a plain-language error without emitting a document', async () => {
      const upload = new StubUploadSupportingDocument();
      upload.failure = new Error('Envoi indisponible');
      const { fixture, host } = createFixture(upload);
      const confirmed: unknown[] = [];
      fixture.componentInstance.confirmed.subscribe((document) => confirmed.push(document));

      selectFile(requiredInput(host, 'input[type="file"]'), new File(['x'], 'facture.pdf'));

      await waitFor(() => fixture.componentInstance.viewModel.hasUploadError());
      fixture.detectChanges();
      expect(host.querySelector('[role="alert"]')?.textContent).toContain('Envoi indisponible');
      expect(confirmed).toEqual([]);
    });
  });
});

const createFixture = (
  upload: StubUploadSupportingDocument,
): { fixture: ComponentFixture<SupportingDocumentUploadWidgetComponent>; host: HTMLElement } => {
  TestBed.configureTestingModule({
    providers: [
      provideTanStackQuery(new QueryClient({ defaultOptions: { queries: { retry: false } } })),
      { provide: UploadSupportingDocument, useValue: upload },
    ],
  });
  const fixture = TestBed.createComponent(SupportingDocumentUploadWidgetComponent);
  fixture.componentRef.setInput('groupId', 'group-1');
  fixture.detectChanges();

  return { fixture, host: fixture.nativeElement };
};

const selectFile = (input: HTMLInputElement, file: File): void => {
  Object.defineProperty(input, 'files', { configurable: true, value: [file] });
  input.dispatchEvent(new Event('change'));
};

const requiredInput = (host: HTMLElement, selector: string): HTMLInputElement => {
  const input = host.querySelector(selector);
  if (!(input instanceof HTMLInputElement)) throw new Error(`Champ absent: ${selector}`);
  return input;
};

const waitFor = async (condition: () => boolean): Promise<void> => {
  for (let attempt = 0; attempt < 50; attempt += 1) {
    if (condition()) return;
    await new Promise((resolve) => setTimeout(resolve));
  }

  throw new Error('La condition attendue n a pas ete atteinte.');
};

class StubUploadSupportingDocument {
  readonly commands: { groupId: string; file: SupportingDocumentFile }[] = [];
  failure: Error | null = null;
  private deferred: Promise<string> | null = null;
  private resolveDeferred: ((intentId: string) => void) | null = null;

  upload(groupId: string, file: SupportingDocumentFile): Promise<string> {
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
