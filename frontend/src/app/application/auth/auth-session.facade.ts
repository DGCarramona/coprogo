import { Injectable, computed, signal } from '@angular/core';

import { GoogleIdTokenPort } from './google-id-token.port';

export type AuthSessionStatus = 'signed-out' | 'signing-in' | 'restoring' | 'ready';

@Injectable({ providedIn: 'root' })
export class AuthSessionFacade {
  private readonly statusState = signal<AuthSessionStatus>('signed-out');
  private readonly errorMessageState = signal<string | null>(null);

  constructor(private readonly idTokenPort: GoogleIdTokenPort) {}

  readonly status = this.statusState.asReadonly();
  readonly errorMessage = this.errorMessageState.asReadonly();
  readonly isBusy = computed(
    () => this.status() === 'signing-in' || this.status() === 'restoring',
  );

  hasStoredToken(): boolean {
    return this.idTokenPort.currentToken() !== null;
  }

  async authenticateWithGoogleIdToken(idToken: string): Promise<boolean> {
    this.idTokenPort.store(idToken);
    this.statusState.set('signing-in');
    this.errorMessageState.set(null);

    this.statusState.set('ready');
    return true;
  }

  async restoreStoredSession(): Promise<boolean> {
    if (!this.hasStoredToken()) {
      this.resetToSignedOut();
      return false;
    }

    this.statusState.set('restoring');
    this.errorMessageState.set(null);

    this.statusState.set('ready');
    return true;
  }

  signOut(): void {
    this.idTokenPort.clear();
    this.resetToSignedOut();
  }

  private resetToSignedOut(): void {
    this.statusState.set('signed-out');
    this.errorMessageState.set(null);
  }
}
