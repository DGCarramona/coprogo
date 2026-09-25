import { TestBed } from '@angular/core/testing';
import { QueryClient } from '@tanstack/angular-query-experimental';

import { ExpenseProposalPort } from './application/expense/expense-proposal.port';
import { GroupMembersPort } from './application/group/group-members.port';
import { NavigationPort } from './application/shared/navigation.port';
import { ExpenseSupportingDocumentDeletionPort } from './application/supporting-document/expense-supporting-document-deletion.port';
import { ExpenseSupportingDocumentsPort } from './application/supporting-document/expense-supporting-documents.port';
import {
  Sha256ChecksumPort,
  SignedSupportingDocumentUploaderPort,
  SupportingDocumentUploadControlPlanePort,
} from './application/supporting-document/supporting-document-upload.port';
import { UploadSupportingDocument } from './application/supporting-document/upload-supporting-document.use-case';
import { HttpExpenseProposalGateway } from './infrastructure/expense/http-expense-proposal.gateway';
import { HttpGroupMembersGateway } from './infrastructure/group/http-group-members.gateway';
import { RouterNavigationAdapter } from './infrastructure/router/router-navigation.adapter';
import { HttpSignedSupportingDocumentUploaderGateway } from './infrastructure/supporting-document/http-signed-supporting-document-uploader.gateway';
import { HttpSupportingDocumentUploadControlPlaneGateway } from './infrastructure/supporting-document/http-supporting-document-upload-control-plane.gateway';
import { HttpExpenseSupportingDocumentDeletionGateway } from './infrastructure/supporting-document/http-expense-supporting-document-deletion.gateway';
import { HttpExpenseSupportingDocumentsGateway } from './infrastructure/supporting-document/http-expense-supporting-documents.gateway';
import { WebCryptoSha256ChecksumGateway } from './infrastructure/supporting-document/web-crypto-sha256-checksum.gateway';
import { appConfig } from './app.config';

describe('appConfig', () => {
  it('provides a TanStack Query client', () => {
    TestBed.configureTestingModule({ providers: appConfig.providers });

    expect(TestBed.inject(QueryClient)).toBeInstanceOf(QueryClient);
  });

  it('binds the expense proposal port to the existing HTTP gateway instance', () => {
    TestBed.configureTestingModule({ providers: appConfig.providers });

    const expenseProposalPort = TestBed.inject(ExpenseProposalPort);

    expect(expenseProposalPort).toBeInstanceOf(HttpExpenseProposalGateway);
    expect(expenseProposalPort).toBe(TestBed.inject(HttpExpenseProposalGateway));
  });

  it('binds the navigation port to the existing router navigation adapter instance', () => {
    TestBed.configureTestingModule({ providers: appConfig.providers });

    const navigationPort = TestBed.inject(NavigationPort);

    expect(navigationPort).toBeInstanceOf(RouterNavigationAdapter);
    expect(navigationPort).toBe(TestBed.inject(RouterNavigationAdapter));
  });

  it('binds the group members port to the existing HTTP gateway instance', () => {
    TestBed.configureTestingModule({ providers: appConfig.providers });

    const groupMembersPort = TestBed.inject(GroupMembersPort);

    expect(groupMembersPort).toBeInstanceOf(HttpGroupMembersGateway);
    expect(groupMembersPort).toBe(TestBed.inject(HttpGroupMembersGateway));
  });

  it('wires the reusable supporting document upload workflow through its ports', () => {
    TestBed.configureTestingModule({ providers: appConfig.providers });

    expect(TestBed.inject(Sha256ChecksumPort)).toBe(TestBed.inject(WebCryptoSha256ChecksumGateway));
    expect(TestBed.inject(SignedSupportingDocumentUploaderPort)).toBe(
      TestBed.inject(HttpSignedSupportingDocumentUploaderGateway),
    );
    expect(TestBed.inject(SupportingDocumentUploadControlPlanePort)).toBe(
      TestBed.inject(HttpSupportingDocumentUploadControlPlaneGateway),
    );
    expect(TestBed.inject(UploadSupportingDocument)).toBeInstanceOf(UploadSupportingDocument);
  });

  it('binds the supporting document deletion port to the existing HTTP gateway instance', () => {
    TestBed.configureTestingModule({ providers: appConfig.providers });

    expect(TestBed.inject(ExpenseSupportingDocumentDeletionPort)).toBe(
      TestBed.inject(HttpExpenseSupportingDocumentDeletionGateway),
    );
  });

  it('binds the supporting documents read port to the existing HTTP gateway instance', () => {
    TestBed.configureTestingModule({ providers: appConfig.providers });

    expect(TestBed.inject(ExpenseSupportingDocumentsPort)).toBe(
      TestBed.inject(HttpExpenseSupportingDocumentsGateway),
    );
  });
});
