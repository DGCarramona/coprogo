export interface AllocationValidationError {
  readonly kind: string;
  readonly message: string;
}

export const parseAmountInCents = (amount: string): number | null => {
  const match = /^(\d+)(?:[,.](\d{1,2}))?$/.exec(amount.trim());
  if (!match) return null;

  const whole = Number(match[1]);
  const fraction = Number((match[2] ?? '').padEnd(2, '0'));
  const cents = whole * 100 + fraction;
  return Number.isSafeInteger(cents) && cents > 0 ? cents : null;
};

export const toggleMember = (members: readonly string[], member: string): readonly string[] =>
  members.includes(member)
    ? members.filter((selectedMember) => selectedMember !== member)
    : [...members, member];
