import {
  formatDate,
  formatMoneyFromCents,
  formatPercentage,
  formatSignedMoneyFromCents,
} from './financial-format';

describe('financial format utilities', () => {
  describe('formatMoneyFromCents', () => {
    it('formats money from cents', () => {
      expect(formatMoneyFromCents(12345)).toBe('123,45 €');
    });
  });

  describe('formatSignedMoneyFromCents', () => {
    it('formats signed money from cents', () => {
      expect(formatSignedMoneyFromCents(12345)).toBe('+123,45 €');
      expect(formatSignedMoneyFromCents(-12345)).toBe('-123,45 €');
    });
  });

  describe('formatPercentage', () => {
    it('formats percentages', () => {
      expect(formatPercentage(12.5)).toBe('12,5 %');
    });
  });

  describe('formatDate', () => {
    it('formats dates', () => {
      expect(formatDate(new Date('2026-04-03T00:00:00Z'))).toContain('2026');
    });
  });
});
