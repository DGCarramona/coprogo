import { Injectable, computed, inject, signal } from '@angular/core';

import { ApiClientError } from '../../infrastructure/api/api-client.error';
import { GroupCreationPort } from '../group/group-creation.port';
import { PendingGroupInvitationsPort } from '../group/pending-group-invitations.port';
import { GoogleIdTokenPort } from './google-id-token.port';

import type { PendingGroupInvitation } from '../../domain/group/pending-group-invitation';

export type AuthSessionStatus = 'signed-out' | 'signing-in' | 'restoring' | 'ready' | 'load-failed';

@Injectable({ providedIn: 'root' })
export class AuthSessionFacade {
  private readonly idTokenPort = inject(GoogleIdTokenPort);
  private readonly pendingGroupInvitationsPort = inject(PendingGroupInvitationsPort);
  private readonly groupCreationPort = inject(GroupCreationPort);
  private readonly statusState = signal<AuthSessionStatus>('signed-out');
  private readonly invitationsState = signal<PendingGroupInvitation[]>([]);
  private readonly errorMessageState = signal<string | null>(null);
  private readonly busyInvitationIdState = signal<string | null>(null);
  private readonly creatingGroupState = signal(false);
  private readonly createdGroupIdState = signal<string | null>(null);

  readonly status = this.statusState.asReadonly();
  readonly invitations = this.invitationsState.asReadonly();
  readonly errorMessage = this.errorMessageState.asReadonly();
  readonly busyInvitationId = this.busyInvitationIdState.asReadonly();
  readonly isCreatingGroup = this.creatingGroupState.asReadonly();
  readonly createdGroupId = this.createdGroupIdState.asReadonly();
  readonly isBusy = computed(
    () =>
      this.status() === 'signing-in' ||
      this.status() === 'restoring' ||
      this.busyInvitationId() !== null ||
      this.isCreatingGroup(),
  );
  readonly hasPendingInvitations = computed(() => this.invitations().length > 0);
  readonly canCreateGroup = computed(
    () => this.status() === 'ready' && !this.hasPendingInvitations() && !this.isCreatingGroup(),
  );

  hasStoredToken(): boolean {
    return this.idTokenPort.currentToken() !== null;
  }

  async authenticateWithGoogleIdToken(idToken: string): Promise<boolean> {
    this.idTokenPort.store(idToken);
    this.statusState.set('signing-in');
    this.errorMessageState.set(null);
    this.createdGroupIdState.set(null);

    return this.loadPendingInvitations();
  }

  async restoreStoredSession(): Promise<boolean> {
    if (!this.hasStoredToken()) {
      this.resetToSignedOut();
      return false;
    }

    this.statusState.set('restoring');
    this.errorMessageState.set(null);

    return this.loadPendingInvitations();
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

  async createGroup(): Promise<void> {
    this.creatingGroupState.set(true);
    this.errorMessageState.set(null);
    this.createdGroupIdState.set(null);

    try {
      this.createdGroupIdState.set(await this.groupCreationPort.create());
    } catch (error) {
      this.errorMessageState.set(describeError(error, 'Le groupe n a pas pu etre cree.'));
    } finally {
      this.creatingGroupState.set(false);
    }
  }

  signOut(): void {
    this.idTokenPort.clear();
    this.resetToSignedOut();
  }

  private async loadPendingInvitations(): Promise<boolean> {
    try {
      const invitations = await this.pendingGroupInvitationsPort.listPending();
      this.invitationsState.set(invitations);
      this.errorMessageState.set(null);
      this.statusState.set('ready');
      return true;
    } catch (error) {
      if (error instanceof ApiClientError && error.status === 401) {
        this.idTokenPort.clear();
        this.resetToSignedOut();
        this.errorMessageState.set('Votre session Google a expire. Reconnectez-vous.');
        return false;
      }

      this.invitationsState.set([]);
      this.statusState.set('load-failed');
      this.errorMessageState.set(
        describeError(error, 'Les invitations en attente n ont pas pu etre chargees.'),
      );
      return false;
    }
  }

  private resetToSignedOut(): void {
    this.statusState.set('signed-out');
    this.invitationsState.set([]);
    this.busyInvitationIdState.set(null);
    this.creatingGroupState.set(false);
    this.createdGroupIdState.set(null);
  }
}

const describeError = (error: unknown, fallbackMessage: string): string =>
  error instanceof Error && error.message.trim().length > 0 ? error.message : fallbackMessage;
