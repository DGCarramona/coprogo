import type { GroupName } from '../../domain/group/group-name';

export abstract class GroupRenamingPort {
  abstract rename(groupId: string, name: GroupName): Promise<void>;
}
