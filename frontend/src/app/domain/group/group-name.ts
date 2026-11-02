import { type Brand, make } from 'ts-brand';

export type GroupName = Brand<string, 'GroupName'>;

const validatedGroupName = make<GroupName>((name) => {
  if (name.length === 0 || name.length > 100) {
    throw new Error('validation.groups.nameLength');
  }
});

export function GroupName(raw: string): GroupName {
  return validatedGroupName(raw.trim());
}
