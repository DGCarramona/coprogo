import { Injectable, signal } from '@angular/core';

import { AuthSessionFacade } from '../../application/auth/auth-session.facade';
import { GoogleIdentityPort } from '../../application/auth/google-identity.port';
import { NavigationPort } from '../../application/shared/navigation.port';
import { describeError } from '../../application/shared/describe-error';

@Injectable()
export class SignInPageViewModel {
  private readonly renderErrorMessageState = signal<string | null>(null);

  constructor(
    private readonly authSessionFacade: AuthSessionFacade,
    private readonly googleIdentityPort: GoogleIdentityPort,
    private readonly navigation: NavigationPort,
  ) {}

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
      await this.navigation.navigateByUrl('/invitations');
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
            await this.navigation.navigateByUrl('/invitations');
          }
        },
      });
    } catch (error) {
      this.renderErrorMessageState.set(
        describeError(error, 'La connexion Google n est pas disponible pour le moment.'),
      );
    }
  }
}
