import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { AuthSessionFacade } from '../../application/auth/auth-session.facade';
import { GoogleIdentityPort } from '../../application/auth/google-identity.port';

@Injectable()
export class SignInPageViewModel {
  private readonly authSessionFacade = inject(AuthSessionFacade);
  private readonly googleIdentityPort = inject(GoogleIdentityPort);
  private readonly router = inject(Router);
  private readonly renderErrorMessageState = signal<string | null>(null);

  readonly renderErrorMessage = this.renderErrorMessageState.asReadonly();

  sessionErrorMessage() {
    return this.authSessionFacade.errorMessage();
  }

  isBusy() {
    const status = this.authSessionFacade.status();
    return status === 'signing-in' || status === 'restoring';
  }

  hasGoogleConfiguration(): boolean {
    return this.googleIdentityPort.isConfigured();
  }

  async initialize(): Promise<void> {
    if (!this.authSessionFacade.hasStoredToken()) {
      return;
    }

    const restored = await this.authSessionFacade.restoreStoredSession();
    if (restored) {
      await this.router.navigateByUrl('/invitations');
    }
  }

  mountGoogleButton(host: HTMLElement): void {
    this.renderErrorMessageState.set(null);

    try {
      this.googleIdentityPort.renderButton({
        host,
        onCredential: async (idToken) => {
          const authenticated = await this.authSessionFacade.authenticateWithGoogleIdToken(idToken);
          if (authenticated) {
            await this.router.navigateByUrl('/invitations');
          }
        },
      });
    } catch (error) {
      this.renderErrorMessageState.set(describeError(error));
    }
  }
}

const describeError = (error: unknown): string =>
  error instanceof Error && error.message.trim().length > 0
    ? error.message
    : 'La connexion Google n est pas disponible pour le moment.';
