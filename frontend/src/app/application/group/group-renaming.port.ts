export abstract class GroupRenamingPort {
  abstract rename(groupId: string, name: string): Promise<void>;
}
