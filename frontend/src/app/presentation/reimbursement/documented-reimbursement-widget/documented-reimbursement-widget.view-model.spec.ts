import { Injector } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { submit } from '@angular/forms/signals';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { Observable, of } from 'rxjs';

import { GroupMembersPort } from '../../../application/group/group-members.port';
import {
  DocumentedReimbursementDeclarationPort,
  type DocumentedReimbursementDeclarationCommand,
} from '../../../application/reimbursement/documented-reimbursement-declaration.port';
import type { GroupMember } from '../../../domain/group/group-member';
import type { SupportingDocumentUploadResult } from '../../shared/supporting-document-upload/supporting-document-upload-result';
import { DocumentedReimbursementWidgetViewModel } from './documented-reimbursement-widget.view-model';

describe('DocumentedReimbursementWidgetViewModel', () => {
  let queryClient: QueryClient;
  let membersPort: StubGroupMembersPort;
  let declarationPort: StubDocumentedReimbursementDeclarationPort;
  let injector: Injector;

  beforeEach(() => {
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    TestBed.configureTestingModule({ providers: [provideTanStackQuery(queryClient)] });
    membersPort = new StubGroupMembersPort();
    declarationPort = new StubDocumentedReimbursementDeclarationPort();
    injector = TestBed.inject(Injector);
  });

  describe('initialize', () => {
    it('loads members and starts with an empty invalid form', async () => {
      const viewModel = createViewModel();

      viewModel.initialize('group-1');
      await waitFor(() => viewModel.members().length === 2);

      expect({
        members: viewModel.members(),
        requestedGroupIds: membersPort.requestedGroupIds,
        form: viewModel.form().value(),
        invalid: viewModel.form().invalid(),
      }).toEqual({
        members: ['alice@example.com', 'bob@example.com'],
        requestedGroupIds: ['group-1'],
        form: {
          receivedBy: '',
          amountInEuros: '',
          reimbursedAt: '',
          documents: [],
        },
        invalid: true,
      });
    });
  });

  describe('form submission', () => {
    it('declares the exact reimbursement and refreshes its group history', async () => {
      queryClient.setQueryData(['groups', 'group-1', 'reimbursements'], ['cached']);
      const viewModel = createViewModel();
      viewModel.initialize('group-1');
      fillForm(viewModel, validFormValue());

      await expect(submit(viewModel.form)).resolves.toBe(true);

      expect(declarationPort.commands).toEqual([
        {
          groupId: 'group-1',
          receivedBy: 'alice@example.com',
          amountInCents: 4250,
          reimbursedAt: new Date('2026-10-03T09:30'),
          supportingDocumentUploadIntents: new Set(['upload-1', 'upload-2']),
        },
      ]);
      expect(
        queryClient.getQueryState(['groups', 'group-1', 'reimbursements'])?.isInvalidated,
      ).toBe(true);
      expect(viewModel.form().value()).toEqual({
        receivedBy: '',
        amountInEuros: '',
        reimbursedAt: '',
        documents: [],
      });
    });

    it.each([
      { name: 'without a receiver', value: validFormValue({ receivedBy: '' }) },
      { name: 'with an invalid amount', value: validFormValue({ amountInEuros: '42,555' }) },
      { name: 'without a date', value: validFormValue({ reimbursedAt: '' }) },
      { name: 'with an invalid date', value: validFormValue({ reimbursedAt: 'invalide' }) },
      { name: 'without a supporting document', value: validFormValue({ documents: [] }) },
    ])('rejects submission $name', async ({ value }) => {
      const viewModel = createViewModel();
      viewModel.initialize('group-1');
      fillForm(viewModel, value);

      await expect(submit(viewModel.form)).resolves.toBe(false);

      expect(declarationPort.commands).toEqual([]);
    });

    it('blocks submission while a supporting document upload is pending', async () => {
      const viewModel = createViewModel();
      viewModel.initialize('group-1');
      fillForm(viewModel, validFormValue());
      viewModel.setSupportingDocumentUploadPending(true);

      await expect(submit(viewModel.form)).resolves.toBe(false);

      expect(declarationPort.commands).toEqual([]);
    });

    it('keeps the complete draft when declaration fails', async () => {
      declarationPort.failure = new Error('Service indisponible');
      const viewModel = createViewModel();
      viewModel.initialize('group-1');
      const value = validFormValue();
      fillForm(viewModel, value);

      await expect(submit(viewModel.form)).resolves.toBe(false);
      await waitFor(() => viewModel.hasDeclarationError());

      expect(viewModel.form().value()).toEqual(value);
      expect(viewModel.declarationErrorMessage()).toBe('Service indisponible');
    });
  });

  describe('supporting document draft', () => {
    it('removes only the selected confirmed document', () => {
      const viewModel = createViewModel();
      viewModel.initialize('group-1');
      viewModel.addSupportingDocument(document('upload-1', 'recu-1.pdf'));
      viewModel.addSupportingDocument(document('upload-2', 'recu-2.pdf'));

      viewModel.removeSupportingDocument('upload-1');

      expect(viewModel.documents()).toEqual([document('upload-2', 'recu-2.pdf')]);
    });
  });

  const createViewModel = (): DocumentedReimbursementWidgetViewModel => {
    const viewModel = new DocumentedReimbursementWidgetViewModel(
      membersPort,
      declarationPort,
      queryClient,
      injector,
    );
    TestBed.tick();
    return viewModel;
  };
});

interface FormValue {
  readonly receivedBy: string;
  readonly amountInEuros: string;
  readonly reimbursedAt: string;
  readonly documents: readonly SupportingDocumentUploadResult[];
}

const validFormValue = (overrides: Partial<FormValue> = {}): FormValue => ({
  receivedBy: 'alice@example.com',
  amountInEuros: '42,50',
  reimbursedAt: '2026-10-03T09:30',
  documents: [document('upload-1', 'recu-1.pdf'), document('upload-2', 'recu-2.pdf')],
  ...overrides,
});

const document = (intentId: string, fileName: string): SupportingDocumentUploadResult => ({
  intentId,
  fileName,
});

const fillForm = (viewModel: DocumentedReimbursementWidgetViewModel, value: FormValue): void => {
  viewModel.form.receivedBy().value.set(value.receivedBy);
  viewModel.form.amountInEuros().value.set(value.amountInEuros);
  viewModel.form.reimbursedAt().value.set(value.reimbursedAt);
  value.documents.forEach((supportingDocument) =>
    viewModel.addSupportingDocument(supportingDocument),
  );
};

class StubGroupMembersPort extends GroupMembersPort {
  readonly requestedGroupIds: string[] = [];

  override listByGroup(groupId: string): Observable<readonly GroupMember[]> {
    this.requestedGroupIds.push(groupId);
    return of([
      { member: 'alice@example.com', joinedAt: new Date('2026-01-01T00:00:00Z') },
      { member: 'bob@example.com', joinedAt: new Date('2026-01-02T00:00:00Z') },
    ]);
  }
}

class StubDocumentedReimbursementDeclarationPort extends DocumentedReimbursementDeclarationPort {
  readonly commands: DocumentedReimbursementDeclarationCommand[] = [];
  failure: Error | null = null;

  override async declare(command: DocumentedReimbursementDeclarationCommand): Promise<void> {
    this.commands.push(command);
    if (this.failure) throw this.failure;
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
