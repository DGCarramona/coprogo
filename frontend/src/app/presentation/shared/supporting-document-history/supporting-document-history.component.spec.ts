import { ComponentFixture, TestBed } from '@angular/core/testing';

import {
  SupportingDocumentHistoryComponent,
  type SupportingDocumentVersion,
} from './supporting-document-history.component';

describe('SupportingDocumentHistoryComponent', () => {
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
});

const createFixture = (
  versions: readonly SupportingDocumentVersion[],
): { fixture: ComponentFixture<SupportingDocumentHistoryComponent>; host: HTMLElement } => {
  const fixture = TestBed.createComponent(SupportingDocumentHistoryComponent);
  fixture.componentRef.setInput('versions', versions);

  return { fixture, host: fixture.nativeElement };
};

const versionItems = (host: HTMLElement): string[] =>
  [...host.querySelectorAll('ol > li')].map(
    (item) => item.textContent?.replace(/\s+/g, ' ').trim() ?? '',
  );

const version = (overrides: Partial<SupportingDocumentVersion>): SupportingDocumentVersion => ({
  sourceUploadIntent: 'intent-1',
  fileName: 'facture.pdf',
  uploader: 'alice@example.com',
  attachedAt: new Date('2026-09-20T09:15:00Z'),
  downloadTarget: {
    url: 'https://documents.example.test/signed/document?signature=do-not-display',
    expiresAt: new Date('2026-09-20T10:15:00Z'),
  },
  replacesSourceUploadIntent: null,
  deletion: null,
  ...overrides,
});
