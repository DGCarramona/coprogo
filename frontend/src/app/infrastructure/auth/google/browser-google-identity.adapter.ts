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
      throw new Error('APP_GOOGLE_CLIENT_ID est requis pour afficher la connexion Google.');
    }

    const google = globalThis.window.google?.accounts.id;
    if (google === undefined) {
      throw new Error('Google Identity Services est indisponible dans ce navigateur.');
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
    google.renderButton(command.host, {
      theme: 'outline',
      size: 'large',
      shape: 'pill',
      text: 'continue_with',
      width: Math.max(command.host.clientWidth, 280),
    });
  }
}
