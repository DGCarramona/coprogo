import { GroupRenamingPort } from '../../../../src/app/application/group/group-renaming.port';
export class StubGroupRenamingPort extends GroupRenamingPort {
  readonly commands: { groupId: string; name: string }[] = [];
  failure: Error | null = null;
  pending: Promise<void> | null = null;
  override async rename(groupId: string, name: string): Promise<void> {
    this.commands.push({ groupId, name });
    if (this.failure) throw this.failure;
    await this.pending;
  }
}
