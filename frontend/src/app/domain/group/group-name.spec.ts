import { expectTypeOf } from 'vitest';
import { GroupName } from './group-name';
import type { MemberEmail } from './member-email';

describe('GroupName', () => {
  it.each([
    [' Appartement familial ', 'Appartement familial'],
    ['a'.repeat(100), 'a'.repeat(100)],
  ])('normalizes %j', (raw, expected) => {
    expect(GroupName(raw)).toBe(expected);
  });
  it.each(['', '  \t\n', 'a'.repeat(101)])('rejects %j', (raw) => {
    expect(() => GroupName(raw)).toThrow('validation.groups.nameLength');
  });
  it('distinguishes a validated group name from raw strings and emails', () => {
    expectTypeOf(GroupName('Appartement')).toEqualTypeOf<GroupName>();
    expectTypeOf<string>().not.toExtend<GroupName>();
    expectTypeOf<MemberEmail>().not.toExtend<GroupName>();
    expectTypeOf<GroupName>().toExtend<string>();
  });
});
