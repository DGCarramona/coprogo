import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';

import { ExpenseListPort } from './application/expense/expense-list.port';
import { ExpenseProposalPort } from './application/expense/expense-proposal.port';
import { NavigationPort } from './application/shared/navigation.port';
import { GoogleIdTokenPort } from './application/auth/google-id-token.port';
import { GoogleIdentityPort } from './application/auth/google-identity.port';
import { GroupCreationPort } from './application/group/group-creation.port';
import { GroupMembersPort } from './application/group/group-members.port';
import { PendingGroupInvitationsPort } from './application/group/pending-group-invitations.port';
import { GroupFinancialDashboardPort } from './application/ledger/group-financial-dashboard.port';
import {
  Sha256ChecksumPort,
  SignedSupportingDocumentUploaderPort,
  SupportingDocumentUploadControlPlanePort,
} from './application/supporting-document/supporting-document-upload.port';
import { ExpenseSupportingDocumentDeletionPort } from './application/supporting-document/expense-supporting-document-deletion.port';
import { ExpenseSupportingDocumentsPort } from './application/supporting-document/expense-supporting-documents.port';
import { provideApiClient } from './infrastructure/api/provide-api-client';
import { RouterNavigationAdapter } from './infrastructure/router/router-navigation.adapter';
import { BrowserGoogleIdTokenStore } from './infrastructure/auth/google/browser-google-id-token.store';
import { BrowserGoogleIdentityAdapter } from './infrastructure/auth/google/browser-google-identity.adapter';
import { HttpExpenseListGateway } from './infrastructure/expense/http-expense-list.gateway';
import { HttpExpenseProposalGateway } from './infrastructure/expense/http-expense-proposal.gateway';
import { HttpGroupCreationGateway } from './infrastructure/group/http-group-creation.gateway';
import { HttpGroupInvitationsGateway } from './infrastructure/group/http-group-invitations.gateway';
import { HttpGroupMembersGateway } from './infrastructure/group/http-group-members.gateway';
import { HttpGroupFinancialDashboardGateway } from './infrastructure/ledger/http-group-financial-dashboard.gateway';
import { HttpSignedSupportingDocumentUploaderGateway } from './infrastructure/supporting-document/http-signed-supporting-document-uploader.gateway';
import { HttpSupportingDocumentUploadControlPlaneGateway } from './infrastructure/supporting-document/http-supporting-document-upload-control-plane.gateway';
import { HttpExpenseSupportingDocumentDeletionGateway } from './infrastructure/supporting-document/http-expense-supporting-document-deletion.gateway';
import { HttpExpenseSupportingDocumentsGateway } from './infrastructure/supporting-document/http-expense-supporting-documents.gateway';
import { WebCryptoSha256ChecksumGateway } from './infrastructure/supporting-document/web-crypto-sha256-checksum.gateway';
import { provideCoprogoHttpClients } from './infrastructure/http/named-http-clients';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideCoprogoHttpClients(),
    provideRouter(routes),
    provideTanStackQuery(new QueryClient()),
    provideApiClient(),
    {
      provide: NavigationPort,
      useExisting: RouterNavigationAdapter,
    },
    {
      provide: GoogleIdTokenPort,
      useExisting: BrowserGoogleIdTokenStore,
    },
    {
      provide: GoogleIdentityPort,
      useExisting: BrowserGoogleIdentityAdapter,
    },
    {
      provide: PendingGroupInvitationsPort,
      useExisting: HttpGroupInvitationsGateway,
    },
    {
      provide: GroupCreationPort,
      useExisting: HttpGroupCreationGateway,
    },
    {
      provide: GroupMembersPort,
      useExisting: HttpGroupMembersGateway,
    },
    {
      provide: GroupFinancialDashboardPort,
      useExisting: HttpGroupFinancialDashboardGateway,
    },
    {
      provide: ExpenseListPort,
      useExisting: HttpExpenseListGateway,
    },
    {
      provide: ExpenseProposalPort,
      useExisting: HttpExpenseProposalGateway,
    },
    {
      provide: Sha256ChecksumPort,
      useExisting: WebCryptoSha256ChecksumGateway,
    },
    {
      provide: SupportingDocumentUploadControlPlanePort,
      useExisting: HttpSupportingDocumentUploadControlPlaneGateway,
    },
    {
      provide: SignedSupportingDocumentUploaderPort,
      useExisting: HttpSignedSupportingDocumentUploaderGateway,
    },
    {
      provide: ExpenseSupportingDocumentDeletionPort,
      useExisting: HttpExpenseSupportingDocumentDeletionGateway,
    },
    {
      provide: ExpenseSupportingDocumentsPort,
      useExisting: HttpExpenseSupportingDocumentsGateway,
    },
  ],
};
