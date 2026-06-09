import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';

import { GoogleIdTokenPort } from './application/auth/google-id-token.port';
import { GoogleIdentityPort } from './application/auth/google-identity.port';
import { GroupCreationPort } from './application/group/group-creation.port';
import { PendingGroupInvitationsPort } from './application/group/pending-group-invitations.port';
import { provideApiClient } from './infrastructure/api/provide-api-client';
import { BrowserGoogleIdTokenStore } from './infrastructure/auth/google/browser-google-id-token.store';
import { BrowserGoogleIdentityAdapter } from './infrastructure/auth/google/browser-google-identity.adapter';
import { HttpGroupCreationGateway } from './infrastructure/group/http-group-creation.gateway';
import { HttpGroupInvitationsGateway } from './infrastructure/group/http-group-invitations.gateway';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideHttpClient(withInterceptorsFromDi()),
    provideAnimationsAsync(),
    provideRouter(routes),
    provideApiClient(),
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
  ],
};
