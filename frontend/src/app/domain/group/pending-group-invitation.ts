export interface PendingGroupInvitation {
  invitationId: string;
  groupId: string;
  invitedMember: string;
  invitedBy: string;
  invitedAt: Date;
}
