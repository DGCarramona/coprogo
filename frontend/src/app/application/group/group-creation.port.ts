export abstract class GroupCreationPort {
  abstract create(name: string): Promise<string>;
}
