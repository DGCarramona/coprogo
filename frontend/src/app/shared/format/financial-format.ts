const euroFormatter = new Intl.NumberFormat('fr-FR', {
  style: 'currency',
  currency: 'EUR',
});

const percentageFormatter = new Intl.NumberFormat('fr-FR', {
  style: 'percent',
  minimumFractionDigits: 0,
  maximumFractionDigits: 2,
});

const dateFormatter = new Intl.DateTimeFormat('fr-FR', {
  dateStyle: 'medium',
});

export const formatMoneyFromCents = (amountInCents: number): string =>
  euroFormatter.format(amountInCents / 100);

export const formatSignedMoneyFromCents = (amountInCents: number): string =>
  amountInCents > 0
    ? `+${formatMoneyFromCents(amountInCents)}`
    : formatMoneyFromCents(amountInCents);

export const formatPercentage = (percentage: number): string =>
  percentageFormatter.format(percentage / 100);

export const formatDate = (date: Date): string => dateFormatter.format(date);
