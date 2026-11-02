export interface MemberGroup {
  readonly groupId: string;
  readonly createdBy: string;
  readonly createdAt: Date;
  readonly name: string | null;
  readonly canRename: boolean;
}
