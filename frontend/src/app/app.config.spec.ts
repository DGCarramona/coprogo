import { ProviderToken } from '@angular/core';
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
