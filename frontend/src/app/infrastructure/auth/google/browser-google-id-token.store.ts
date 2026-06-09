import { Injectable } from '@angular/core';

import { GoogleIdTokenPort } from '../../../application/auth/google-id-token.port';

const GOOGLE_ID_TOKEN_STORAGE_KEY = 'coprogo.google-id-token';

@Injectable({ providedIn: 'root' })
export class BrowserGoogleIdTokenStore extends GoogleIdTokenPort {
  override currentToken(): string | null {
    return globalThis.sessionStorage.getItem(GOOGLE_ID_TOKEN_STORAGE_KEY);
  }

  override store(token: string): void {
    globalThis.sessionStorage.setItem(GOOGLE_ID_TOKEN_STORAGE_KEY, token);
  }

  override clear(): void {
    globalThis.sessionStorage.removeItem(GOOGLE_ID_TOKEN_STORAGE_KEY);
  }
}
