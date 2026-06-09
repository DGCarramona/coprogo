export abstract class GroupCreationPort {
  abstract create(): Promise<string>;
}
