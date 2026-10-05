import type { ExpenseAllocation } from '../../../application/expense/expense-proposal.port';
import { AllocationValidationError, parseAmountInCents } from './expense-proposal-form';

export interface CustomAllocationFormModel {
  readonly amountsInEurosByMember: Readonly<Partial<Record<string, string>>>;
}

type CustomAllocation = Extract<ExpenseAllocation, { type: 'CUSTOM' }>;

export const emptyCustomAllocationForm = (): CustomAllocationFormModel => ({
  amountsInEurosByMember: {},
});

export const setCustomAmount = (
  form: CustomAllocationFormModel,
  member: string,
  amountInEuros: string,
): CustomAllocationFormModel => ({
  amountsInEurosByMember:
    amountInEuros.trim().length === 0
      ? Object.fromEntries(
          Object.entries(form.amountsInEurosByMember).filter(
            ([participatingMember]) => participatingMember !== member,
          ),
        )
      : { ...form.amountsInEurosByMember, [member]: amountInEuros },
});

export const validateCustomAllocationForm = (
  form: CustomAllocationFormModel,
  totalAmountInCents: number,
): AllocationValidationError | undefined => {
  const amounts = Object.values(form.amountsInEurosByMember);
  if (amounts.length === 0) {
    return {
      kind: 'participations',
      message: 'Indiquez un montant pour au moins un participant.',
    };
  }

  const amountsInCents = amounts.map((amount) =>
    amount === undefined ? null : parseAmountInCents(amount),
  );
  const validAmountsInCents = amountsInCents.filter((amount): amount is number => amount !== null);
  if (validAmountsInCents.length !== amounts.length) {
    return {
      kind: 'amount',
      message: 'Indiquez des montants positifs avec deux decimales au plus.',
    };
  }

  const participationTotal = validAmountsInCents.reduce((total, amount) => total + amount, 0);
  return participationTotal === totalAmountInCents
    ? undefined
    : {
        kind: 'total',
        message: 'La somme des montants doit correspondre au montant total.',
      };
};

export const toCustomAllocation = (form: CustomAllocationFormModel): CustomAllocation => ({
  type: 'CUSTOM',
  amountsInCentsByMember: new Map(
    Object.entries(form.amountsInEurosByMember).flatMap(
      ([member, amountInEuros]): [string, number][] => {
        const amountInCents =
          amountInEuros === undefined ? null : parseAmountInCents(amountInEuros);
        return amountInCents === null ? [] : [[member, amountInCents]];
      },
    ),
  ),
});
