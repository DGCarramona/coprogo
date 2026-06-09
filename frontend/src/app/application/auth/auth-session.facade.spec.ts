import { TestBed } from '@angular/core/testing';

import { AuthSessionFacade } from './auth-session.facade';
import { GoogleIdTokenPort } from './google-id-token.port';

import { GroupCreationPort } from '../group/group-creation.port';
import { PendingGroupInvitationsPort } from '../group/pending-group-invitations.port';
import type { PendingGroupInvitation } from '../../domain/group/pending-group-invitation';
import { ApiClientError } from '../../infrastructure/api/api-client.error';

class InMemoryGoogleIdTokenPort extends GoogleIdTokenPort {
  private token: string | null = null;

  override currentToken(): string | null {
    return this.token;
  }

  override store(token: string): void {
    this.token = token;
  }

  override clear(): void {
    this.token = null;
  }
}

class FakePendingGroupInvitationsPort extends PendingGroupInvitationsPort {
  invitations: PendingGroupInvitation[] = [];
  listPendingCalls = 0;
  acceptedInvitations: string[] = [];
  listPendingError: Error | null = null;
  acceptError: Error | null = null;

  override async listPending(): Promise<PendingGroupInvitation[]> {
    this.listPendingCalls += 1;
    if (this.listPendingError !== null) {
      throw this.listPendingError;
    }

    return this.invitations;
  }

  override async accept(invitationId: string): Promise<void> {
    if (this.acceptError !== null) {
      throw this.acceptError;
    }

    this.acceptedInvitations.push(invitationId);
  }
}

class FakeGroupCreationPort extends GroupCreationPort {
  createdGroupId = 'group-1';
  createCalls = 0;
  createError: Error | null = null;

  override async create(): Promise<string> {
    this.createCalls += 1;
    if (this.createError !== null) {
      throw this.createError;
    }

    return this.createdGroupId;
  }
}

describe('AuthSessionFacade', () => {
  const createFacade = (
    overrides: {
      idTokenPort?: GoogleIdTokenPort;
      invitationsPort?: PendingGroupInvitationsPort;
      groupCreationPort?: GroupCreationPort;
    } = {},
  ) => {
    TestBed.configureTestingModule({
      providers: [
        AuthSessionFacade,
        {
          provide: GoogleIdTokenPort,
          useValue: overrides.idTokenPort ?? new InMemoryGoogleIdTokenPort(),
        },
        {
          provide: PendingGroupInvitationsPort,
          useValue: overrides.invitationsPort ?? new FakePendingGroupInvitationsPort(),
        },
        {
          provide: GroupCreationPort,
          useValue: overrides.groupCreationPort ?? new FakeGroupCreationPort(),
        },
      ],
    });

    return TestBed.inject(AuthSessionFacade);
  };

  it('authenticates with a Google id token and loads pending invitations', async () => {
    const idTokenPort = new InMemoryGoogleIdTokenPort();
    const invitationsPort = new FakePendingGroupInvitationsPort();
    const groupCreationPort = new FakeGroupCreationPort();
    invitationsPort.invitations = [
      {
        invitationId: 'invitation-1',
        groupId: 'group-1',
        invitedMember: 'alice@example.com',
        invitedBy: 'bob@example.com',
        invitedAt: new Date('2026-04-15T09:00:00Z'),
      },
    ];
    const facade = createFacade({ idTokenPort, invitationsPort, groupCreationPort });

    const authenticated = await facade.authenticateWithGoogleIdToken('google-id-token');

    expect(authenticated).toBe(true);
    expect(idTokenPort.currentToken()).toBe('google-id-token');
    expect(facade.status()).toBe('ready');
    expect(facade.invitations()).toEqual(invitationsPort.invitations);
  });

  it('clears the stored token when the backend rejects the restored session', async () => {
    const idTokenPort = new InMemoryGoogleIdTokenPort();
    const invitationsPort = new FakePendingGroupInvitationsPort();
    const groupCreationPort = new FakeGroupCreationPort();
    const facade = createFacade({ idTokenPort, invitationsPort, groupCreationPort });
    idTokenPort.store('expired-id-token');
    invitationsPort.listPendingError = new ApiClientError('unauthorized', 401);

    const restored = await facade.restoreStoredSession();

    expect(restored).toBe(false);
    expect(idTokenPort.currentToken()).toBeNull();
    expect(facade.status()).toBe('signed-out');
    expect(facade.errorMessage()).toBe('Votre session Google a expire. Reconnectez-vous.');
  });

  it('accepts an invitation and removes it from the current list', async () => {
    const idTokenPort = new InMemoryGoogleIdTokenPort();
    const invitationsPort = new FakePendingGroupInvitationsPort();
    const groupCreationPort = new FakeGroupCreationPort();
    const facade = createFacade({ idTokenPort, invitationsPort, groupCreationPort });
    invitationsPort.invitations = [
      {
        invitationId: 'invitation-1',
        groupId: 'group-1',
        invitedMember: 'alice@example.com',
        invitedBy: 'bob@example.com',
        invitedAt: new Date('2026-04-15T09:00:00Z'),
      },
      {
        invitationId: 'invitation-2',
        groupId: 'group-2',
        invitedMember: 'alice@example.com',
        invitedBy: 'carol@example.com',
        invitedAt: new Date('2026-04-15T10:00:00Z'),
      },
    ];

    await facade.authenticateWithGoogleIdToken('google-id-token');
    await facade.acceptInvitation('invitation-1');

    expect(invitationsPort.acceptedInvitations).toEqual(['invitation-1']);
    expect(facade.invitations()).toEqual([invitationsPort.invitations[1]]);
  });

  it('creates a group when the authenticated member has no invitation', async () => {
    const idTokenPort = new InMemoryGoogleIdTokenPort();
    const invitationsPort = new FakePendingGroupInvitationsPort();
    const groupCreationPort = new FakeGroupCreationPort();
    const facade = createFacade({ idTokenPort, invitationsPort, groupCreationPort });

    await facade.authenticateWithGoogleIdToken('google-id-token');
    await facade.createGroup();

    expect(groupCreationPort.createCalls).toBe(1);
    expect(facade.createdGroupId()).toBe('group-1');
  });
});
