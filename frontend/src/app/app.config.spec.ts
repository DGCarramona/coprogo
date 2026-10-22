import { ProviderToken } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { QueryClient } from '@tanstack/angular-query-experimental';

import { ExpenseProposalPort } from './application/expense/expense-proposal.port';
import { ExpenseDetailPort } from './application/expense/expense-detail.port';
import { ExpenseParticipationDecisionPort } from './application/expense/expense-participation-decision.port';
import { MemberDebtReductionHistoryPort } from './application/ledger/member-debt-reduction-history.port';
import { GroupMembersPort } from './application/group/group-members.port';
import { NavigationPort } from './application/shared/navigation.port';
import { ReimbursementListPort } from './application/reimbursement/reimbursement-list.port';
import { DirectReimbursementRecordingPort } from './application/reimbursement/direct-reimbursement-recording.port';
import { DocumentedReimbursementDeclarationPort } from './application/reimbursement/documented-reimbursement-declaration.port';
import { OwnershipShareTimelinePort } from './application/revenue/ownership-share-timeline.port';
import { ExpenseSupportingDocumentDeletionPort } from './application/supporting-document/expense-supporting-document-deletion.port';
import { ExpenseSupportingDocumentsPort } from './application/supporting-document/expense-supporting-documents.port';
import {
  Sha256ChecksumPort,
  SignedSupportingDocumentUploaderPort,
  SupportingDocumentUploadControlPlanePort,
} from './application/supporting-document/supporting-document-upload.port';
import { UploadSupportingDocument } from './application/supporting-document/upload-supporting-document.use-case';
import { HttpExpenseProposalGateway } from './infrastructure/expense/http-expense-proposal.gateway';
import { HttpExpenseDetailGateway } from './infrastructure/expense/http-expense-detail.gateway';
import { HttpExpenseParticipationDecisionGateway } from './infrastructure/expense/http-expense-participation-decision.gateway';
import { HttpMemberDebtReductionHistoryGateway } from './infrastructure/ledger/http-member-debt-reduction-history.gateway';
import { HttpGroupMembersGateway } from './infrastructure/group/http-group-members.gateway';
import { HttpReimbursementListGateway } from './infrastructure/reimbursement/http-reimbursement-list.gateway';
import { HttpDirectReimbursementRecordingGateway } from './infrastructure/reimbursement/http-direct-reimbursement-recording.gateway';
import { HttpDocumentedReimbursementDeclarationGateway } from './infrastructure/reimbursement/http-documented-reimbursement-declaration.gateway';
import { HttpOwnershipShareTimelineGateway } from './infrastructure/revenue/http-ownership-share-timeline.gateway';
import { RouterNavigationAdapter } from './infrastructure/router/router-navigation.adapter';
import { HttpSignedSupportingDocumentUploaderGateway } from './infrastructure/supporting-document/http-signed-supporting-document-uploader.gateway';
import { HttpSupportingDocumentUploadControlPlaneGateway } from './infrastructure/supporting-document/http-supporting-document-upload-control-plane.gateway';
import { HttpExpenseSupportingDocumentDeletionGateway } from './infrastructure/supporting-document/http-expense-supporting-document-deletion.gateway';
import { HttpExpenseSupportingDocumentsGateway } from './infrastructure/supporting-document/http-expense-supporting-documents.gateway';
import { WebCryptoSha256ChecksumGateway } from './infrastructure/supporting-document/web-crypto-sha256-checksum.gateway';
import { appConfig } from './app.config';

const portBindings: readonly {
  name: string;
  port: ProviderToken<unknown>;
  adapter: ProviderToken<unknown>;
}[] = [
  {
    name: 'expense proposal',
    port: ExpenseProposalPort,
    adapter: HttpExpenseProposalGateway,
  },
  {
    name: 'expense detail',
    port: ExpenseDetailPort,
    adapter: HttpExpenseDetailGateway,
  },
  {
    name: 'expense participation decision',
    port: ExpenseParticipationDecisionPort,
    adapter: HttpExpenseParticipationDecisionGateway,
  },
  {
    name: 'navigation',
    port: NavigationPort,
    adapter: RouterNavigationAdapter,
  },
  {
    name: 'group members',
    port: GroupMembersPort,
    adapter: HttpGroupMembersGateway,
  },
  {
    name: 'reimbursements read',
    port: ReimbursementListPort,
    adapter: HttpReimbursementListGateway,
  },
  {
    name: 'member debt reduction history',
    port: MemberDebtReductionHistoryPort,
    adapter: HttpMemberDebtReductionHistoryGateway,
  },
  {
    name: 'direct reimbursement recording',
    port: DirectReimbursementRecordingPort,
    adapter: HttpDirectReimbursementRecordingGateway,
  },
  {
    name: 'documented reimbursement declaration',
    port: DocumentedReimbursementDeclarationPort,
    adapter: HttpDocumentedReimbursementDeclarationGateway,
  },
  {
    name: 'ownership share timeline',
    port: OwnershipShareTimelinePort,
    adapter: HttpOwnershipShareTimelineGateway,
  },
  {
    name: 'SHA-256 checksum',
    port: Sha256ChecksumPort,
    adapter: WebCryptoSha256ChecksumGateway,
  },
  {
    name: 'signed supporting document uploader',
    port: SignedSupportingDocumentUploaderPort,
    adapter: HttpSignedSupportingDocumentUploaderGateway,
  },
  {
    name: 'supporting document upload control plane',
    port: SupportingDocumentUploadControlPlanePort,
    adapter: HttpSupportingDocumentUploadControlPlaneGateway,
  },
  {
    name: 'supporting document deletion',
    port: ExpenseSupportingDocumentDeletionPort,
    adapter: HttpExpenseSupportingDocumentDeletionGateway,
  },
  {
    name: 'supporting documents read',
    port: ExpenseSupportingDocumentsPort,
    adapter: HttpExpenseSupportingDocumentsGateway,
  },
];

describe('appConfig', () => {
  it('provides a TanStack Query client', () => {
    TestBed.configureTestingModule({ providers: appConfig.providers });

    expect(TestBed.inject(QueryClient)).toBeInstanceOf(QueryClient);
  });

  it.each(portBindings)(
    'binds the $name port to the existing adapter instance',
    ({ port, adapter }) => {
      TestBed.configureTestingModule({ providers: appConfig.providers });

      expect(TestBed.inject(port)).toBe(TestBed.inject(adapter));
    },
  );

  it('wires the reusable supporting document upload workflow through its ports', () => {
    TestBed.configureTestingModule({ providers: appConfig.providers });

    expect(TestBed.inject(UploadSupportingDocument)).toBeInstanceOf(UploadSupportingDocument);
  });
});
