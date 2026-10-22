import type { ExpenseStatus } from '../../domain/expense/expense-summary';

export const toExpenseStatus = (status: string): ExpenseStatus => {
  switch (status) {
    case 'PROPOSED':
    case 'ACCEPTED':
    case 'INVALIDATED':
      return status;
    default:
      throw new Error(`Statut de depense inconnu: ${status}.`);
  }
};

export const toExpenseCreationDate = (value: string): Date => {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    throw new Error(`Date de creation de depense invalide: ${value}.`);
  }

  return date;
};
