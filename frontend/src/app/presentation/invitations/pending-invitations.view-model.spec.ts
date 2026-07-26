import { AuthSessionFacade } from '../../application/auth/auth-session.facade';
import { PendingGroupInvitationsPort } from '../../application/group/pending-group-invitations.port';
import {
  authSessionFacadeWithToken,
  defaultAuthSessionFacade,
} from '../../../../__test__/app/application/auth/stub-google-id-token.port';
import { StubNavigationPort } from '../../../../__test__/app/application/shared/stub-navigation.port';
import { PendingGroupInvitation } from '../../domain/group/pending-group-invitation';
import { PendingInvitationsViewModel } from './pending-invitations.view-model';

describe('PendingInvitationsViewModel', () => {
  it('restores the session and loads pending invitations', async () => {
    const invitationsPort = new StubPendingGroupInvitationsPort();
    invitationsPort.invitations = [pendingInvitation('invitation-1')];
    const viewModel = createViewModel({ invitationsPort });

    await viewModel.initialize();

    expect(viewModel.isLoading()).toBe(false);
    expect(viewModel.invitations()).toEqual([pendingInvitation('invitation-1')]);
    expect(viewModel.isEmpty()).toBe(false);
  });

  it('redirects to sign-in when no token is stored', async () => {
    const navigation = new StubNavigationPort();
    const viewModel = createViewModel({
      authSessionFacade: authSessionFacadeWithToken(false),
      navigation,
    });

    await viewModel.initialize();

    expect(navigation.navigatedTo).toBe('/connexion');
  });

  it('accepts an invitation and removes it locally', async () => {
    const invitationsPort = new StubPendingGroupInvitationsPort();
    invitationsPort.invitations = [
      pendingInvitation('invitation-1'),
      pendingInvitation('invitation-2'),
    ];
    const viewModel = createViewModel({ invitationsPort });
    await viewModel.initialize();

    await viewModel.acceptInvitation('invitation-1');

    expect(invitationsPort.acceptedInvitations).toEqual(['invitation-1']);
    expect(viewModel.invitations()).toEqual([pendingInvitation('invitation-2')]);
  });

  it('exposes load errors', async () => {
    const invitationsPort = new StubPendingGroupInvitationsPort();
    invitationsPort.failure = new Error('Backend indisponible');
    const viewModel = createViewModel({ invitationsPort });

    await viewModel.initialize();

    expect(viewModel.hasLoadError()).toBe(true);
    expect(viewModel.errorMessage()).toBe('Backend indisponible');
  });
});

const defaultNavigation = new StubNavigationPort();

const createViewModel = ({
  authSessionFacade = defaultAuthSessionFacade,
  invitationsPort = new StubPendingGroupInvitationsPort(),
  navigation = defaultNavigation,
}: {
  authSessionFacade?: AuthSessionFacade;
  invitationsPort?: StubPendingGroupInvitationsPort;
  navigation?: StubNavigationPort;
} = {}): PendingInvitationsViewModel =>
  new PendingInvitationsViewModel(authSessionFacade, invitationsPort, navigation);

const pendingInvitation = (invitationId: string): PendingGroupInvitation => ({
  invitationId,
  groupId: `group-${invitationId}`,
  invitedMember: 'alice@example.com',
  invitedBy: 'bob@example.com',
  invitedAt: new Date('2026-04-15T09:00:00Z'),
});

class StubPendingGroupInvitationsPort extends PendingGroupInvitationsPort {
  invitations: PendingGroupInvitation[] = [];
  acceptedInvitations: string[] = [];
  failure: Error | null = null;

  override async listPending(): Promise<PendingGroupInvitation[]> {
    if (this.failure) {
      throw this.failure;
    }

    return this.invitations;
  }

  override async accept(invitationId: string): Promise<void> {
    this.acceptedInvitations.push(invitationId);
  }
}
