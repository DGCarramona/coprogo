import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';

import {
  ExpenseSupportingDocumentDeletionPort,
  type DeleteExpenseSupportingDocumentCommand,
} from '../../../application/supporting-document/expense-supporting-document-deletion.port';
import {
  SupportingDocumentHistoryComponent,
  type SupportingDocumentVersion,
} from './supporting-document-history.component';

describe('SupportingDocumentHistoryComponent', () => {
  let deletionPort: StubExpenseSupportingDocumentDeletionPort;

  beforeEach(() => {
    deletionPort = new StubExpenseSupportingDocumentDeletionPort();
    TestBed.configureTestingModule({
      providers: [
        provideTanStackQuery(new QueryClient({ defaultOptions: { queries: { retry: false } } })),
        { provide: ExpenseSupportingDocumentDeletionPort, useValue: deletionPort },
      ],
    });
  });

  describe('versions', () => {
    it('renders the supplied chronological history with its attachment audit', () => {
      const { fixture, host } = createFixture([
        version({
          sourceUploadIntent: 'intent-original',
          fileName: 'facture-initiale.pdf',
          uploader: 'alice@example.com',
          attachedAt: new Date('2026-09-20T09:15:00Z'),
          downloadTarget: {
            url: 'https://documents.example.test/signed/original?signature=do-not-display',
            expiresAt: new Date('2026-09-20T10:15:00Z'),
          },
        }),
        version({
          sourceUploadIntent: 'intent-replacement',
          fileName: 'facture-corrigee.pdf',
          uploader: 'bob@example.com',
          attachedAt: new Date('2026-09-22T10:30:00Z'),
          replacesSourceUploadIntent: 'intent-original',
          deletion: {
            deletedBy: 'bob@example.com',
            deletedAt: new Date('2026-09-23T11:45:00Z'),
          },
          downloadTarget: {
            url: 'https://documents.example.test/signed/replacement?signature=do-not-display',
            expiresAt: new Date('2026-09-22T11:30:00Z'),
          },
        }),
      ]);

      fixture.detectChanges();

      expect(host.querySelector('h3')?.textContent).toBe('Historique des versions du justificatif');
      expect(versionItems(host)).toEqual([
        expect.stringContaining('facture-initiale.pdf'),
        expect.stringContaining('facture-corrigee.pdf'),
      ]);
      expect(versionItems(host)[0]).toContain('Ajouté par alice@example.com');
      expect(versionItems(host)[1]).toContain('Remplace une version précédente.');
      expect(versionItems(host)[1]).toContain('Supprimé par bob@example.com');
      expect([...host.querySelectorAll('time')].map((time) => time.dateTime)).toEqual([
        '2026-09-20T09:15:00.000Z',
        '2026-09-22T10:30:00.000Z',
        '2026-09-23T11:45:00.000Z',
      ]);
      expect(host.textContent).not.toContain('intent-original');
      expect(host.textContent).not.toContain('intent-replacement');
      expect(
        [...host.querySelectorAll('a')].map((link) => ({
          text: link.textContent?.replace(/\s+/g, ' ').trim(),
          href: link.getAttribute('href'),
          target: link.getAttribute('target'),
          rel: link.getAttribute('rel'),
          referrerPolicy: link.getAttribute('referrerpolicy'),
        })),
      ).toEqual([
        {
          text: 'Consulter ou télécharger facture-initiale.pdf',
          href: 'https://documents.example.test/signed/original?signature=do-not-display',
          target: '_blank',
          rel: 'noopener noreferrer',
          referrerPolicy: 'no-referrer',
        },
        {
          text: 'Consulter ou télécharger facture-corrigee.pdf',
          href: 'https://documents.example.test/signed/replacement?signature=do-not-display',
          target: '_blank',
          rel: 'noopener noreferrer',
          referrerPolicy: 'no-referrer',
        },
      ]);
      expect(host.textContent).not.toContain('https://documents.example.test/signed/original');
      expect(host.textContent).not.toContain('https://documents.example.test/signed/replacement');
      expect(host.querySelectorAll('button')).toHaveLength(0);
    });

    it('explains when the justificatif has no version', () => {
      const { fixture, host } = createFixture([]);

      fixture.detectChanges();

      expect(host.querySelector('ol')).toBeNull();
      expect(host.textContent).toContain('Aucun justificatif n’a été ajouté.');
    });
  });

  describe('retirer le justificatif', () => {
    it('proposes removal only for the latest non-deleted version when permitted', () => {
      const { fixture, host } = createFixture([
        version({
          sourceUploadIntent: 'intent-original',
          fileName: 'facture-initiale.pdf',
          canDelete: true,
        }),
        version({
          sourceUploadIntent: 'intent-current',
          fileName: 'facture-courante.pdf',
          replacesSourceUploadIntent: 'intent-original',
          canDelete: true,
        }),
      ]);

      fixture.detectChanges();

      expect(
        [...host.querySelectorAll('button')].map((button) => ({
          text: button.textContent?.trim(),
          accessibleName: button.getAttribute('aria-label'),
          disabled: button.hasAttribute('disabled'),
        })),
      ).toEqual([
        {
          text: 'Retirer',
          accessibleName: 'Retirer le justificatif facture-courante.pdf',
          disabled: false,
        },
      ]);
    });

    it.each([
      ['the latest version cannot be removed', version({ canDelete: false })],
      [
        'the latest version was already removed',
        version({
          canDelete: true,
          deletion: { deletedBy: 'alice@example.com', deletedAt: new Date('2026-09-24T12:00:00Z') },
        }),
      ],
    ])('does not propose removal when %s', (_description, latestVersion) => {
      const { fixture, host } = createFixture([latestVersion]);

      fixture.detectChanges();

      expect(host.querySelector('button')).toBeNull();
    });

    it('shows pending state, delegates the exact document, and keeps it visible after success', async () => {
      deletionPort.defer();
      const currentVersion = version({
        sourceUploadIntent: 'intent-current',
        fileName: 'facture-courante.pdf',
        canDelete: true,
      });
      const { fixture, host } = createFixture([currentVersion]);
      fixture.detectChanges();

      host.querySelector<HTMLButtonElement>('button')?.click();
      fixture.detectChanges();

      await waitFor(() => deletionPort.commands.length === 1);
      expect(deletionPort.commands).toEqual([
        { groupId: 'group-1', expenseId: 'expense-1', sourceUploadIntent: 'intent-current' },
      ]);
      expect(host.querySelector<HTMLButtonElement>('button')?.disabled).toBe(true);
      expect(host.querySelector('[role="status"]')?.textContent?.trim()).toBe(
        'Retrait du justificatif en cours…',
      );

      deletionPort.resolve();
      await waitFor(() => host.querySelector('[role="status"]')?.textContent?.includes('retiré'));
      fixture.detectChanges();

      expect(host.querySelector('[role="status"]')?.textContent?.trim()).toBe(
        'Le justificatif a été retiré. Il reste visible dans l’historique.',
      );
      expect(versionItems(host)).toEqual([expect.stringContaining('facture-courante.pdf')]);
      expect(host.querySelector('button')).toBeNull();
    });

    it('shows the deletion error without removing the version from the displayed history', async () => {
      deletionPort.failure = new Error('Le retrait est indisponible.');
      const { fixture, host } = createFixture([
        version({ fileName: 'facture-courante.pdf', canDelete: true }),
      ]);
      fixture.detectChanges();

      host.querySelector<HTMLButtonElement>('button')?.click();
      await waitFor(() => host.querySelector('[role="alert"]') !== null);
      fixture.detectChanges();

      expect(host.querySelector('[role="alert"]')?.textContent?.trim()).toBe(
        'Le retrait est indisponible.',
      );
      expect(versionItems(host)).toEqual([expect.stringContaining('facture-courante.pdf')]);
    });
  });
});

const createFixture = (
  versions: readonly SupportingDocumentVersion[],
): { fixture: ComponentFixture<SupportingDocumentHistoryComponent>; host: HTMLElement } => {
  const fixture = TestBed.createComponent(SupportingDocumentHistoryComponent);
  fixture.componentRef.setInput('versions', versions);
  fixture.componentRef.setInput('groupId', 'group-1');
  fixture.componentRef.setInput('expenseId', 'expense-1');

  return { fixture, host: fixture.nativeElement };
};

const versionItems = (host: HTMLElement): string[] =>
  [...host.querySelectorAll('ol > li')].map(
    (item) => item.textContent?.replace(/\s+/g, ' ').trim() ?? '',
  );

const version = (overrides: Partial<SupportingDocumentVersion>): SupportingDocumentVersion => ({
  sourceUploadIntent: overrides.sourceUploadIntent ?? 'intent-1',
  fileName: overrides.fileName ?? 'facture.pdf',
  mediaType: overrides.mediaType ?? 'application/pdf',
  sizeBytes: overrides.sizeBytes ?? 1234,
  uploader: overrides.uploader ?? 'alice@example.com',
  attachedAt: overrides.attachedAt ?? new Date('2026-09-20T09:15:00Z'),
  downloadTarget: overrides.downloadTarget ?? {
    url: 'https://documents.example.test/signed/document?signature=do-not-display',
    expiresAt: new Date('2026-09-20T10:15:00Z'),
  },
  replacesSourceUploadIntent: overrides.replacesSourceUploadIntent ?? null,
  deletion: overrides.deletion ?? null,
  canDelete: overrides.canDelete ?? false,
});

class StubExpenseSupportingDocumentDeletionPort extends ExpenseSupportingDocumentDeletionPort {
  readonly commands: DeleteExpenseSupportingDocumentCommand[] = [];
  failure: Error | null = null;
  private deferred: Promise<void> | null = null;
  private resolveDeferred: (() => void) | null = null;

  override delete(command: DeleteExpenseSupportingDocumentCommand): Promise<void> {
    this.commands.push(command);
    if (this.failure !== null) return Promise.reject(this.failure);
    return this.deferred ?? Promise.resolve();
  }

  defer(): void {
    this.deferred = new Promise((resolve) => {
      this.resolveDeferred = resolve;
    });
  }

  resolve(): void {
    this.resolveDeferred?.();
  }
}

const waitFor = async (condition: () => boolean | undefined): Promise<void> => {
  for (let attempt = 0; attempt < 50; attempt += 1) {
    TestBed.tick();
    if (condition()) return;
    await new Promise((resolve) => setTimeout(resolve));
  }

  throw new Error('La condition attendue n a pas ete atteinte.');
};
