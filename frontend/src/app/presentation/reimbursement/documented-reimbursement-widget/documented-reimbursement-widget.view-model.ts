import { computed, Injectable, Injector, signal } from '@angular/core';
import { FieldTree, TreeValidationResult, form, required, validate } from '@angular/forms/signals';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';

import { GroupMembersPort } from '../../../application/group/group-members.port';
import {
  DocumentedReimbursementDeclarationPort,
  type DocumentedReimbursementDeclarationCommand,
} from '../../../application/reimbursement/documented-reimbursement-declaration.port';
import { describeError } from '../../../application/shared/describe-error';
import { parseAmountInCents } from '../../../shared/format/parse-amount-in-cents';
import { parseLocalDateTime } from '../../../shared/format/parse-local-date-time';
import { groupMembersQueryOptions } from '../../shared/group-members/group-members-query';
import type { SupportingDocumentUploadResult } from '../../shared/supporting-document-upload/supporting-document-upload-result';

interface DocumentedReimbursementFormModel {
  receivedBy: string;
  amountInEuros: string;
  reimbursedAt: string;
  documents: readonly SupportingDocumentUploadResult[];
}

@Injectable()
export class DocumentedReimbursementWidgetViewModel {
  private readonly groupIdState = signal<string | null>(null);
  private readonly formModel = signal<DocumentedReimbursementFormModel>(emptyFormModel());
  private readonly uploadPendingState = signal(false);
  readonly form: FieldTree<DocumentedReimbursementFormModel>;

  private readonly membersQuery;
  readonly members = computed<readonly string[]>(() => this.membersQuery.data() ?? []);
  readonly isLoading = computed(() => this.membersQuery.isLoading());
  readonly hasLoadError = computed(() => this.membersQuery.isError());
  readonly loadErrorMessage = computed(() => {
    const error = this.membersQuery.error();
    return error === null
      ? null
      : describeError(error, 'Les membres du groupe n ont pas pu etre charges.');
  });

  private readonly declarationMutation;
  readonly documents = computed(() => this.formModel().documents);
  readonly isSupportingDocumentUploadPending = this.uploadPendingState.asReadonly();
  readonly isDeclaring = computed(() => this.form().submitting());
  readonly isDeclared = computed(() => this.declarationMutation.isSuccess());
  readonly hasDeclarationError = computed(() => this.declarationMutation.isError());
  readonly declarationErrorMessage = computed(() => {
    const error = this.declarationMutation.error();
    return error === null
      ? null
      : describeError(error, 'Le remboursement n a pas pu etre declare.');
  });

  constructor(
    private readonly groupMembersPort: GroupMembersPort,
    private readonly declarationPort: DocumentedReimbursementDeclarationPort,
    private readonly queryClient: QueryClient,
    injector: Injector,
  ) {
    this.membersQuery = injectQuery(
      () => groupMembersQueryOptions(this.groupIdState(), this.groupMembersPort),
      { injector },
    );
    this.declarationMutation = injectMutation<
      void,
      Error,
      DocumentedReimbursementDeclarationCommand
    >(
      () => ({
        mutationFn: (command) => this.declarationPort.declare(command),
        onSuccess: (_, command) =>
          this.queryClient.invalidateQueries({
            queryKey: ['groups', command.groupId, 'reimbursements'],
          }),
      }),
      { injector },
    );
    this.form = form(
      this.formModel,
      (reimbursement) => {
        required(reimbursement.receivedBy, {
          message: 'Choisissez la personne que vous avez remboursee.',
        });
        validate(reimbursement.amountInEuros, ({ value }) =>
          parseAmountInCents(value()) === null
            ? {
                kind: 'amount',
                message: 'Indiquez un montant positif avec deux decimales au plus.',
              }
            : undefined,
        );
        required(reimbursement.reimbursedAt, { message: 'Indiquez la date du remboursement.' });
        validate(reimbursement.reimbursedAt, ({ value }) =>
          parseLocalDateTime(value()) === null
            ? { kind: 'date', message: 'Indiquez une date de remboursement valide.' }
            : undefined,
        );
        validate(reimbursement.documents, ({ value }) =>
          value().length === 0
            ? { kind: 'documents', message: 'Ajoutez au moins un justificatif.' }
            : undefined,
        );
      },
      {
        injector,
        submission: {
          action: async (submittedForm): Promise<TreeValidationResult> => {
            if (this.isSupportingDocumentUploadPending()) {
              return {
                kind: 'supporting-document-upload',
                message: 'Attendez la fin de l envoi du justificatif.',
              };
            }

            const value = submittedForm().value();
            const groupId = this.groupIdState();
            const amountInCents = parseAmountInCents(value.amountInEuros);
            const reimbursedAt = parseLocalDateTime(value.reimbursedAt);
            if (
              groupId === null ||
              amountInCents === null ||
              reimbursedAt === null ||
              value.documents.length === 0
            ) {
              return {
                kind: 'documented-reimbursement',
                message: 'Completez les informations et ajoutez un justificatif.',
              };
            }

            try {
              await this.declarationMutation.mutateAsync({
                groupId,
                receivedBy: value.receivedBy,
                amountInCents,
                reimbursedAt,
                supportingDocumentUploadIntents: new Set(
                  value.documents.map((document) => document.intentId),
                ),
              });
              submittedForm().reset(emptyFormModel());
              this.uploadPendingState.set(false);
              return undefined;
            } catch {
              return {
                kind: 'documented-reimbursement',
                message: 'Le remboursement n a pas pu etre declare.',
              };
            }
          },
        },
      },
    );
  }

  initialize(groupId: string): void {
    this.groupIdState.set(groupId);
    this.form().reset(emptyFormModel());
    this.uploadPendingState.set(false);
  }

  retry(): void {
    void this.membersQuery.refetch();
  }

  addSupportingDocument(document: SupportingDocumentUploadResult): void {
    this.formModel.update((model) =>
      model.documents.some(({ intentId }) => intentId === document.intentId)
        ? model
        : { ...model, documents: [...model.documents, document] },
    );
  }

  removeSupportingDocument(intentId: string): void {
    this.formModel.update((model) => ({
      ...model,
      documents: model.documents.filter((document) => document.intentId !== intentId),
    }));
  }

  setSupportingDocumentUploadPending(isPending: boolean): void {
    this.uploadPendingState.set(isPending);
  }
}

const emptyFormModel = (): DocumentedReimbursementFormModel => ({
  receivedBy: '',
  amountInEuros: '',
  reimbursedAt: '',
  documents: [],
});
