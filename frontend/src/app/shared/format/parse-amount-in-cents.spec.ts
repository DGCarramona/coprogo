import { parseAmountInCents } from './parse-amount-in-cents';

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
