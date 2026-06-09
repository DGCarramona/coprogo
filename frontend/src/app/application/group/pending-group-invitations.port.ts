import { PendingGroupInvitation } from '../../domain/group/pending-group-invitation';

export abstract class PendingGroupInvitationsPort {
  abstract listPending(): Promise<PendingGroupInvitation[]>;

  abstract accept(invitationId: string): Promise<void>;
}
