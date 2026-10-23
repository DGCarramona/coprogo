import { toOwnershipShares } from './ownership-share-change-form';

describe('toOwnershipShares', () => {
  it('converts one percentage for every member into basis-point-safe numbers', () => {
    expect(
      toOwnershipShares(['alice@example.com', 'bob@example.com'], {
        'alice@example.com': '60,25',
        'bob@example.com': '39.75',
      }),
    ).toEqual([
      { member: 'alice@example.com', percentage: 60.25 },
      { member: 'bob@example.com', percentage: 39.75 },
    ]);
  });

  it.each([
    {
      name: 'a missing member percentage',
      members: ['alice@example.com', 'bob@example.com'],
      percentages: { 'alice@example.com': '100' },
    },
    {
      name: 'a zero percentage',
      members: ['alice@example.com', 'bob@example.com'],
      percentages: { 'alice@example.com': '100', 'bob@example.com': '0' },
    },
    {
      name: 'more than two decimals',
      members: ['alice@example.com', 'bob@example.com'],
      percentages: { 'alice@example.com': '60.001', 'bob@example.com': '39.999' },
    },
    {
      name: 'a total below 100 percent',
      members: ['alice@example.com', 'bob@example.com'],
      percentages: { 'alice@example.com': '60', 'bob@example.com': '39' },
    },
    {
      name: 'no member',
      members: [],
      percentages: {},
    },
  ])('rejects $name', ({ members, percentages }) => {
    expect(toOwnershipShares(members, percentages)).toBeNull();
  });
});
