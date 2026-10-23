import type { OwnershipShareInput } from '../../application/revenue/ownership-share-change.port';

export const toOwnershipShares = (
  members: readonly string[],
  percentagesByMember: Readonly<Partial<Record<string, string>>>,
): readonly OwnershipShareInput[] | null => {
  if (members.length === 0 || new Set(members).size !== members.length) return null;

  const shares = members.map((member) => {
    const basisPoints = parseBasisPoints(percentagesByMember[member] ?? '');
    return basisPoints === null ? null : { member, basisPoints };
  });
  if (shares.some((share) => share === null)) return null;

  const validShares = shares.filter((share) => share !== null);
  if (validShares.reduce((total, share) => total + share.basisPoints, 0) !== 10_000) return null;

  return validShares.map(({ member, basisPoints }) => ({
    member,
    percentage: basisPoints / 100,
  }));
};

const parseBasisPoints = (value: string): number | null => {
  const match = /^(\d+)(?:[.,](\d{1,2}))?$/.exec(value.trim());
  if (match === null) return null;

  const basisPoints = Number(match[1]) * 100 + Number((match[2] ?? '').padEnd(2, '0'));
  return Number.isSafeInteger(basisPoints) && basisPoints > 0 && basisPoints <= 10_000
    ? basisPoints
    : null;
};
