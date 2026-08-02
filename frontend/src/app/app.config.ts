import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';

import { ExpenseListPort } from './application/expense/expense-list.port';
import { ExpenseProposalPort } from './application/expense/expense-proposal.port';
import { NavigationPort } from './application/shared/navigation.port';
import { GoogleIdTokenPort } from './application/auth/google-id-token.port';
import { GoogleIdentityPort } from './application/auth/google-identity.port';
import { GroupCreationPort } from './application/group/group-creation.port';
import { GroupMembersPort } from './application/group/group-members.port';
import { PendingGroupInvitationsPort } from './application/group/pending-group-invitations.port';
import { GroupFinancialDashboardPort } from './application/ledger/group-financial-dashboard.port';
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
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideHttpClient(withInterceptorsFromDi()),
    provideAnimationsAsync(),
    provideRouter(routes),
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
  ],
};
