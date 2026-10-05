import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ReimbursementSupportingDocumentsComponent } from './reimbursement-supporting-documents.component';

describe('ReimbursementSupportingDocumentsComponent', () => {
  it('renders every attached document with a protected direct download link', () => {
    const fixture = createFixture([
      {
        sourceUploadIntent: 'upload-intent-1',
        fileName: 'recu.pdf',
        uploader: 'bob@example.com',
        attachedAt: '1 oct. 2026',
        downloadUrl: 'https://documents.example.test/recu.pdf',
      },
    ]);

    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const link = host.querySelector<HTMLAnchorElement>('a');
    expect({
      text: host.textContent,
      href: link?.href,
      target: link?.target,
      rel: link?.rel,
      referrerPolicy: link?.getAttribute('referrerpolicy'),
    }).toEqual({
      text: expect.stringContaining('recu.pdf'),
      href: 'https://documents.example.test/recu.pdf',
      target: '_blank',
      rel: 'noopener noreferrer',
      referrerPolicy: 'no-referrer',
    });
    expect(host.textContent).toContain('bob@example.com');
    expect(host.textContent).toContain('1 oct. 2026');
  });

  it('states clearly that no document is attached', () => {
    const fixture = createFixture([]);

    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect({
      emptyState: host.querySelector('[role="status"]') !== null,
      downloadLink: host.querySelector('a'),
    }).toEqual({ emptyState: true, downloadLink: null });
  });
});

const createFixture = (
  documents: readonly {
    sourceUploadIntent: string;
    fileName: string;
    uploader: string;
    attachedAt: string;
    downloadUrl: string;
  }[],
): ComponentFixture<ReimbursementSupportingDocumentsComponent> => {
  TestBed.configureTestingModule({ imports: [ReimbursementSupportingDocumentsComponent] });
  const fixture = TestBed.createComponent(ReimbursementSupportingDocumentsComponent);
  fixture.componentRef.setInput('documents', documents);
  return fixture;
};
