import { Component, Input, output } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { Observable, of } from 'rxjs';

import { GroupMembersPort } from '../../../application/group/group-members.port';
import {
  DocumentedReimbursementDeclarationPort,
  type DocumentedReimbursementDeclarationCommand,
} from '../../../application/reimbursement/documented-reimbursement-declaration.port';
import type { GroupMember } from '../../../domain/group/group-member';
import type { SupportingDocumentUploadResult } from '../../shared/supporting-document-upload/supporting-document-upload-result';
import { SupportingDocumentUploadWidgetComponent } from '../../shared/supporting-document-upload/supporting-document-upload-widget.component';
import { DocumentedReimbursementWidgetComponent } from './documented-reimbursement-widget.component';

@Component({ selector: 'app-supporting-document-upload-widget', template: '' })
class StubSupportingDocumentUploadWidgetComponent {
  @Input({ required: true }) groupId = '';
  readonly confirmed = output<SupportingDocumentUploadResult>();
  readonly uploadPendingChange = output<boolean>();
}

describe('DocumentedReimbursementWidgetComponent', () => {
  it('declares the reimbursement with the confirmed supporting document', async () => {
    const declarationPort = new StubDocumentedReimbursementDeclarationPort();
    const { fixture, host } = createFixture(declarationPort);
    await waitForMembers(fixture, host);

    setValue(host, fixture, 'select[aria-label="Personne remboursee"]', 'alice@example.com');
    setValue(host, fixture, 'input[aria-label="Montant rembourse en euros"]', '42,50');
    setValue(host, fixture, 'input[aria-label="Date du remboursement"]', '2026-10-03T09:30');
    const upload = requiredUploadWidget(fixture);
    upload.confirmed.emit({ intentId: 'upload-1', fileName: 'recu.pdf' });
    fixture.detectChanges();

    const submitButton = requiredButton(host, 'button[type="submit"]');
    expect(submitButton.disabled).toBe(false);
    submitButton.click();

    await waitFor(() => declarationPort.commands.length === 1);
    expect(declarationPort.commands).toEqual([
      {
        groupId: 'group-1',
        receivedBy: 'alice@example.com',
        amountInCents: 4250,
        reimbursedAt: new Date('2026-10-03T09:30'),
        supportingDocumentUploadIntents: new Set(['upload-1']),
      },
    ]);
  });

  it('requires a confirmed supporting document', async () => {
    const { fixture, host } = createFixture(new StubDocumentedReimbursementDeclarationPort());
    await waitForMembers(fixture, host);

    setValue(host, fixture, 'select[aria-label="Personne remboursee"]', 'alice@example.com');
    setValue(host, fixture, 'input[aria-label="Montant rembourse en euros"]', '42,50');
    setValue(host, fixture, 'input[aria-label="Date du remboursement"]', '2026-10-03T09:30');

    expect(requiredButton(host, 'button[type="submit"]').disabled).toBe(true);
  });

  it('blocks declaration while a supporting document upload is pending', async () => {
    const { fixture, host } = createFixture(new StubDocumentedReimbursementDeclarationPort());
    await waitForMembers(fixture, host);
    setValue(host, fixture, 'select[aria-label="Personne remboursee"]', 'alice@example.com');
    setValue(host, fixture, 'input[aria-label="Montant rembourse en euros"]', '42,50');
    setValue(host, fixture, 'input[aria-label="Date du remboursement"]', '2026-10-03T09:30');
    const upload = requiredUploadWidget(fixture);
    upload.confirmed.emit({ intentId: 'upload-1', fileName: 'recu.pdf' });
    upload.uploadPendingChange.emit(true);
    fixture.detectChanges();

    expect(requiredButton(host, 'button[type="submit"]').disabled).toBe(true);
  });

  it('lets the user remove a confirmed supporting document', async () => {
    const { fixture, host } = createFixture(new StubDocumentedReimbursementDeclarationPort());
    await waitForMembers(fixture, host);
    requiredUploadWidget(fixture).confirmed.emit({ intentId: 'upload-1', fileName: 'recu.pdf' });
    fixture.detectChanges();

    requiredButton(host, 'button[aria-label="Retirer recu.pdf"]').click();
    fixture.detectChanges();

    expect(host.textContent).not.toContain('recu.pdf');
  });
});

const createFixture = (
  declarationPort: StubDocumentedReimbursementDeclarationPort,
): { fixture: ComponentFixture<DocumentedReimbursementWidgetComponent>; host: HTMLElement } => {
  TestBed.configureTestingModule({
    imports: [DocumentedReimbursementWidgetComponent],
    providers: [
      provideTanStackQuery(new QueryClient({ defaultOptions: { queries: { retry: false } } })),
      { provide: GroupMembersPort, useClass: StubGroupMembersPort },
      { provide: DocumentedReimbursementDeclarationPort, useValue: declarationPort },
    ],
  });
  TestBed.overrideComponent(DocumentedReimbursementWidgetComponent, {
    remove: { imports: [SupportingDocumentUploadWidgetComponent] },
    add: { imports: [StubSupportingDocumentUploadWidgetComponent] },
  });
  const fixture = TestBed.createComponent(DocumentedReimbursementWidgetComponent);
  fixture.componentRef.setInput('groupId', 'group-1');
  return { fixture, host: fixture.nativeElement as HTMLElement };
};

class StubGroupMembersPort extends GroupMembersPort {
  override listByGroup(): Observable<readonly GroupMember[]> {
    return of([
      { member: 'alice@example.com', joinedAt: new Date('2026-01-01T00:00:00Z') },
      { member: 'bob@example.com', joinedAt: new Date('2026-01-02T00:00:00Z') },
    ]);
  }
}

class StubDocumentedReimbursementDeclarationPort extends DocumentedReimbursementDeclarationPort {
  readonly commands: DocumentedReimbursementDeclarationCommand[] = [];

  override async declare(command: DocumentedReimbursementDeclarationCommand): Promise<void> {
    this.commands.push(command);
  }
}

const requiredUploadWidget = (
  fixture: ComponentFixture<DocumentedReimbursementWidgetComponent>,
): StubSupportingDocumentUploadWidgetComponent => {
  const upload = fixture.debugElement.query(
    By.directive(StubSupportingDocumentUploadWidgetComponent),
  );
  if (upload === null) throw new Error('Widget d upload absent');
  return upload.componentInstance as StubSupportingDocumentUploadWidgetComponent;
};

const waitForMembers = async (
  fixture: ComponentFixture<DocumentedReimbursementWidgetComponent>,
  host: HTMLElement,
): Promise<void> =>
  waitFor(() => {
    fixture.detectChanges();
    return host.querySelector('option[value="alice@example.com"]') !== null;
  });

const setValue = (
  host: HTMLElement,
  fixture: ComponentFixture<DocumentedReimbursementWidgetComponent>,
  selector: string,
  value: string,
): void => {
  const field = host.querySelector<HTMLInputElement | HTMLSelectElement>(selector);
  if (field === null) throw new Error(`Champ absent: ${selector}`);
  field.value = value;
  field.dispatchEvent(new Event('input'));
  fixture.detectChanges();
};

const requiredButton = (host: HTMLElement, selector: string): HTMLButtonElement => {
  const button = host.querySelector<HTMLButtonElement>(selector);
  if (button === null) throw new Error(`Bouton absent: ${selector}`);
  return button;
};

const waitFor = async (condition: () => boolean): Promise<void> => {
  for (let attempt = 0; attempt < 50; attempt += 1) {
    TestBed.tick();
    if (condition()) return;
    await new Promise((resolve) => setTimeout(resolve));
  }

  throw new Error('La condition attendue n a pas ete atteinte.');
};
