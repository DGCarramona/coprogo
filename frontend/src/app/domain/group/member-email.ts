import { type Brand, make } from 'ts-brand';

export type MemberEmail = Brand<string, 'MemberEmail'>;

const validatedMemberEmail = make<MemberEmail>((email) => {
  if (!/^[^\s@]+@[^\s@]+$/.test(email)) {
    throw new Error('validation.members.email');
  }
});

export function MemberEmail(raw: string): MemberEmail {
  return validatedMemberEmail(raw.trim().toLowerCase());
}
