import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';

import { AuthSessionFacade } from '../../application/auth/auth-session.facade';

@Injectable()
export class PendingInvitationsPageViewModel {
  private readonly authSessionFacade = inject(AuthSessionFacade);
  private readonly router = inject(Router);

  invitations() {
    return this.authSessionFacade.invitations();
  }

  errorMessage() {
    return this.authSessionFacade.errorMessage();
  }

  busyInvitationId() {
    return this.authSessionFacade.busyInvitationId();
  }

  createdGroupId() {
    return this.authSessionFacade.createdGroupId();
  }

  isCreatingGroup() {
    return this.authSessionFacade.isCreatingGroup();
  }

  isLoading() {
    const status = this.authSessionFacade.status();
    return status === 'signing-in' || status === 'restoring';
  }

  hasLoadError() {
    return this.authSessionFacade.status() === 'load-failed';
  }

  isEmpty() {
    return (
      this.authSessionFacade.status() === 'ready' &&
      this.authSessionFacade.invitations().length === 0
    );
  }

  async initialize(): Promise<void> {
    if (!this.authSessionFacade.hasStoredToken()) {
      await this.router.navigateByUrl('/connexion');
      return;
    }

    const status = this.authSessionFacade.status();
    if (status === 'ready' || status === 'load-failed') {
      return;
    }

    const restored = await this.authSessionFacade.restoreStoredSession();
    if (!restored && !this.authSessionFacade.hasStoredToken()) {
      await this.router.navigateByUrl('/connexion');
    }
  }

  async retry(): Promise<void> {
    const restored = await this.authSessionFacade.restoreStoredSession();
    if (!restored && !this.authSessionFacade.hasStoredToken()) {
      await this.router.navigateByUrl('/connexion');
    }
  }

  async acceptInvitation(invitationId: string): Promise<void> {
    await this.authSessionFacade.acceptInvitation(invitationId);
  }

  async createGroup(): Promise<void> {
    await this.authSessionFacade.createGroup();
  }

  async signOut(): Promise<void> {
    this.authSessionFacade.signOut();
    await this.router.navigateByUrl('/connexion');
  }
}
