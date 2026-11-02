import type { GroupName } from '../../domain/group/group-name';

export abstract class GroupCreationPort {
  abstract create(name: GroupName): Promise<string>;
}
