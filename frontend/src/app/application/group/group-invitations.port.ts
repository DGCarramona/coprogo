export interface SentGroupInvitation {
  readonly invitation: string;
  readonly invitedMember: string;
  readonly invitedBy: string;
  readonly invitedAt: Date;
  readonly canCancel: boolean;
}

export abstract class GroupInvitationsPort {
  abstract list(groupId: string): Promise<readonly SentGroupInvitation[]>;
  abstract cancel(groupId: string, invitationId: string): Promise<void>;
}
