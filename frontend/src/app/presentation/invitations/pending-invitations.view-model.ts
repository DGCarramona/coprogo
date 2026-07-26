import { Injectable, signal } from '@angular/core';

import { AuthSessionFacade } from '../../application/auth/auth-session.facade';
import { NavigationPort } from '../../application/shared/navigation.port';
import { PendingGroupInvitationsPort } from '../../application/group/pending-group-invitations.port';
import { describeError } from '../../application/shared/describe-error';
import { PendingGroupInvitation } from '../../domain/group/pending-group-invitation';

type PendingInvitationsStatus = 'idle' | 'loading' | 'ready' | 'load-failed';

@Injectable()
export class PendingInvitationsViewModel {
  private readonly statusState = signal<PendingInvitationsStatus>('idle');
  private readonly invitationsState = signal<PendingGroupInvitation[]>([]);
  private readonly errorMessageState = signal<string | null>(null);
  private readonly busyInvitationIdState = signal<string | null>(null);

  constructor(
    private readonly authSessionFacade: AuthSessionFacade,
    private readonly pendingGroupInvitationsPort: PendingGroupInvitationsPort,
    private readonly navigation: NavigationPort,
  ) {}

  invitations() {
    return this.invitationsState();
  }

  errorMessage() {
    return this.errorMessageState();
  }

  busyInvitationId() {
    return this.busyInvitationIdState();
  }

  isLoading() {
    return this.statusState() === 'loading';
  }

  hasLoadError() {
    return this.statusState() === 'load-failed';
  }

  isEmpty() {
    return this.statusState() === 'ready' && this.invitationsState().length === 0;
  }

  async initialize(): Promise<void> {
    if (!this.authSessionFacade.hasStoredToken()) {
      await this.navigation.navigateByUrl('/connexion');
      return;
    }

    if (this.statusState() === 'ready' || this.statusState() === 'load-failed') return;
    if (
      !(await this.authSessionFacade.restoreStoredSession()) &&
      !this.authSessionFacade.hasStoredToken()
    ) {
      await this.navigation.navigateByUrl('/connexion');
      return;
    }

    await this.loadPendingInvitations();
  }

  async retry(): Promise<void> {
    if (!this.authSessionFacade.hasStoredToken()) {
      await this.navigation.navigateByUrl('/connexion');
      return;
    }

    await this.loadPendingInvitations();
  }

  async acceptInvitation(invitationId: string): Promise<void> {
    this.busyInvitationIdState.set(invitationId);
    this.errorMessageState.set(null);

    try {
      await this.pendingGroupInvitationsPort.accept(invitationId);
      this.invitationsState.update((invitations) =>
        invitations.filter((invitation) => invitation.invitationId !== invitationId),
      );
    } catch (error) {
      this.errorMessageState.set(describeError(error, "L'invitation n'a pas pu etre acceptee."));
    } finally {
      this.busyInvitationIdState.set(null);
    }
  }

  private async loadPendingInvitations(): Promise<void> {
    this.statusState.set('loading');
    this.errorMessageState.set(null);

    try {
      this.invitationsState.set(await this.pendingGroupInvitationsPort.listPending());
      this.statusState.set('ready');
    } catch (error) {
      this.invitationsState.set([]);
      this.statusState.set('load-failed');
      this.errorMessageState.set(
        describeError(error, 'Les invitations en attente n ont pas pu etre chargees.'),
      );
    }
  }
}
