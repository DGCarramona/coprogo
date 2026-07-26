import {
  formatDate,
  formatMoneyFromCents,
  formatPercentage,
  formatSignedMoneyFromCents,
} from './financial-format';

describe('financial format utilities', () => {
  it('formats money from cents', () => {
    expect(formatMoneyFromCents(12345)).toBe('123,45 €');
  });

  it('formats signed money from cents', () => {
    expect(formatSignedMoneyFromCents(12345)).toBe('+123,45 €');
    expect(formatSignedMoneyFromCents(-12345)).toBe('-123,45 €');
  });

  it('formats percentages', () => {
    expect(formatPercentage(12.5)).toBe('12,5 %');
  });

  it('formats dates', () => {
    expect(formatDate(new Date('2026-04-03T00:00:00Z'))).toContain('2026');
  });
});
