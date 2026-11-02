import { expectTypeOf } from 'vitest';
import { MemberEmail } from './member-email';
import type { GroupName } from './group-name';

describe('MemberEmail', () => {
  it('trims and lowercases an email', () => {
    expect(MemberEmail('  Alice@Example.com  ')).toBe('alice@example.com');
  });
  it.each(['', 'alice', '@example.com', 'alice@', 'a@@b', 'a b@example.com', 'a@b c'])(
    'rejects %s',
    (raw) => {
      expect(() => MemberEmail(raw)).toThrow('validation.members.email');
    },
  );
  it('distinguishes a validated email from raw strings and group names', () => {
    expectTypeOf(MemberEmail('alice@example.com')).toEqualTypeOf<MemberEmail>();
    expectTypeOf<string>().not.toExtend<MemberEmail>();
    expectTypeOf<GroupName>().not.toExtend<MemberEmail>();
    expectTypeOf<MemberEmail>().toExtend<string>();
  });
});
