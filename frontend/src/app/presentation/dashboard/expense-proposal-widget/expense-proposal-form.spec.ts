import { parseAmountInCents, toggleMember } from './expense-proposal-form';

describe('parseAmountInCents', () => {
  it.each([
    ['12', 1200],
    ['12,5', 1250],
    ['12,50', 1250],
    ['12.50', 1250],
  ])('parses %s into cents', (amount, expected) => {
    expect(parseAmountInCents(amount)).toBe(expected);
  });

  it.each(['', '0', '-1', '12,345', 'not-an-amount'])('rejects %s', (amount) => {
    expect(parseAmountInCents(amount)).toBeNull();
  });
});

describe('toggleMember', () => {
  it('adds or removes a member without mutating the source', () => {
    const members = ['alice@example.com'];

    expect(toggleMember(members, 'bob@example.com')).toEqual([
      'alice@example.com',
      'bob@example.com',
    ]);
    expect(toggleMember(members, 'alice@example.com')).toEqual([]);
    expect(members).toEqual(['alice@example.com']);
  });
});
