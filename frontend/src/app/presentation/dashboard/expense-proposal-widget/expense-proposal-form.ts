export interface AllocationValidationError {
  readonly kind: string;
  readonly message: string;
}

export const toggleMember = (members: readonly string[], member: string): readonly string[] =>
  members.includes(member)
    ? members.filter((selectedMember) => selectedMember !== member)
    : [...members, member];
