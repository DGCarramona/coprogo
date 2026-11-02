export const formatMoneyFromCents = (amountInCents: number, locale = 'fr-FR'): string =>
  new Intl.NumberFormat(locale, { style: 'currency', currency: 'EUR' }).format(amountInCents / 100);

export const formatSignedMoneyFromCents = (amountInCents: number, locale = 'fr-FR'): string =>
  `${amountInCents > 0 ? '+' : ''}${formatMoneyFromCents(amountInCents, locale)}`;

export const formatPercentage = (percentage: number, locale = 'fr-FR'): string =>
  new Intl.NumberFormat(locale, {
    style: 'percent',
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  }).format(percentage / 100);

export const formatDate = (date: Date, locale = 'fr-FR'): string =>
  new Intl.DateTimeFormat(locale, { dateStyle: 'medium' }).format(date);
