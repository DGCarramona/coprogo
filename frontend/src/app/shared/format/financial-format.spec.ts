import { TestBed } from '@angular/core/testing';

import { FinancialFormatter } from '../../presentation/i18n/financial-formatter';
import { LocalizationService } from '../../presentation/i18n/localization.service';
import { provideLocalization } from '../../presentation/i18n/provide-localization';
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
    it.each(['fr-FR', 'pt-PT'])('formats instants in the user timezone using %s', (locale) => {
      const date = new Date('2026-03-31T23:30:00Z');
      expect(formatDate(date, locale)).toBe(
        new Intl.DateTimeFormat(locale, { dateStyle: 'medium' }).format(date),
      );
    });
  });
});

describe('FinancialFormatter', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideLocalization()] });
  });

  describe('date', () => {
    it.each([
      ['fr', 'fr-FR'],
      ['pt', 'pt-PT'],
    ] as const)('formats an instant in the user timezone in %s', (language, locale) => {
      TestBed.inject(LocalizationService).selectLanguage(language);
      const date = new Date('2026-03-31T23:30:00Z');
      expect(TestBed.inject(FinancialFormatter).date(date)).toBe(
        new Intl.DateTimeFormat(locale, { dateStyle: 'medium' }).format(date),
      );
    });
  });

  describe('civilDate', () => {
    it.each([
      ['fr', new Date(2026, 3, 1), '1 avr. 2026'],
      ['fr', new Date(2026, 9, 25), '25 oct. 2026'],
      ['pt', new Date(2026, 3, 1), '01/04/2026'],
      ['pt', new Date(2026, 9, 25), '25/10/2026'],
    ] as const)('formats the calendar day in %s', (language, date, expected) => {
      TestBed.inject(LocalizationService).selectLanguage(language);
      expect(TestBed.inject(FinancialFormatter).civilDate(date)).toBe(expected);
    });
  });

  describe('dateTime', () => {
    it.each([
      ['fr', 'fr-FR'],
      ['pt', 'pt-PT'],
    ] as const)('formats an instant in the user timezone in %s', (language, locale) => {
      TestBed.inject(LocalizationService).selectLanguage(language);
      const date = new Date('2026-03-31T23:30:00Z');
      expect(TestBed.inject(FinancialFormatter).dateTime(date)).toBe(
        new Intl.DateTimeFormat(locale, {
          day: 'numeric',
          month: 'short',
          year: 'numeric',
          hour: '2-digit',
          minute: '2-digit',
        }).format(date),
      );
    });
  });
});
