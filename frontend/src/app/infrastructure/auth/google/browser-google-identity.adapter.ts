import { Injectable } from '@angular/core';

import {
  GoogleIdentityPort,
  RenderGoogleSignInButtonCommand,
} from '../../../application/auth/google-identity.port';

@Injectable({ providedIn: 'root' })
export class BrowserGoogleIdentityAdapter extends GoogleIdentityPort {
  private readonly clientId = import.meta.env.APP_GOOGLE_CLIENT_ID?.trim() ?? '';

  override isConfigured(): boolean {
    return this.clientId.length > 0;
  }

  override renderButton(command: RenderGoogleSignInButtonCommand): void {
    if (!this.isConfigured()) {
      throw new Error('errors.auth.googleNotConfigured');
    }

    const google = globalThis.window.google?.accounts.id;
    if (google === undefined) {
      throw new Error('errors.auth.googleUnavailable');
    }

    command.host.replaceChildren();
    google.initialize({
      client_id: this.clientId,
      callback: ({ credential }) => {
        void command.onCredential(credential);
      },
      context: 'signin',
      ux_mode: 'popup',
    });
    google.renderButton(
      command.host,
      googleSignInButtonOptions(command.host.clientWidth, command.locale),
    );
  }
}

export const googleSignInButtonOptions = (
  availableWidth: number,
  locale: string,
): GoogleIdentityButtonConfiguration => ({
  theme: 'outline',
  size: 'large',
  shape: 'pill',
  text: 'continue_with',
  width: Math.min(400, Math.max(availableWidth, 200)),
  locale,
});
