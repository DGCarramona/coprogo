export interface GroupMember {
  readonly member: string;
  readonly joinedAt: Date;
  readonly addedBy: string | null;
}
