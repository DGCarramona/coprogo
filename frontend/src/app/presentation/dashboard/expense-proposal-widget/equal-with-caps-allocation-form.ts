import type { ExpenseAllocation } from '../../../application/expense/expense-proposal.port';
import {
  AllocationValidationError,
  parseAmountInCents,
  toggleMember,
} from './expense-proposal-form';

export interface EqualWithCapsFormModel {
  readonly participants: readonly string[];
  readonly maximumAmountsInEuros: Readonly<Partial<Record<string, string>>>;
}

type EqualWithCapsAllocation = Extract<ExpenseAllocation, { type: 'EQUAL_WITH_CAPS' }>;

export const emptyEqualWithCapsForm = (): EqualWithCapsFormModel => ({
  participants: [],
  maximumAmountsInEuros: {},
});

export const toggleEqualWithCapsParticipant = (
  form: EqualWithCapsFormModel,
  member: string,
): EqualWithCapsFormModel => {
  const isSelected = form.participants.includes(member);

  return {
    participants: toggleMember(form.participants, member),
    maximumAmountsInEuros: isSelected
      ? withoutMember(form.maximumAmountsInEuros, member)
      : form.maximumAmountsInEuros,
  };
};

export const setEqualWithCapsMaximum = (
  form: EqualWithCapsFormModel,
  member: string,
  amountInEuros: string,
): EqualWithCapsFormModel => {
  if (!form.participants.includes(member)) return form;

  return {
    ...form,
    maximumAmountsInEuros:
      amountInEuros.trim().length === 0
        ? withoutMember(form.maximumAmountsInEuros, member)
        : { ...form.maximumAmountsInEuros, [member]: amountInEuros },
  };
};

export const validateEqualWithCapsForm = (
  form: EqualWithCapsFormModel,
): AllocationValidationError | undefined => {
  if (form.participants.length === 0) {
    return { kind: 'participants', message: 'Choisissez au moins un participant.' };
  }

  const hasInvalidMaximum = form.participants.some((member) => {
    const maximum = form.maximumAmountsInEuros[member]?.trim() ?? '';
    return maximum.length > 0 && parseAmountInCents(maximum) === null;
  });
  if (hasInvalidMaximum) {
    return {
      kind: 'maximum-amount',
      message: 'Indiquez un montant maximum positif avec deux decimales au plus.',
    };
  }

  const hasUncappedParticipant = form.participants.some(
    (member) => (form.maximumAmountsInEuros[member]?.trim() ?? '').length === 0,
  );
  return hasUncappedParticipant
    ? undefined
    : {
        kind: 'uncapped-participant',
        message: 'Laissez au moins un participant sans montant maximum.',
      };
};

export const toEqualWithCapsAllocation = (
  form: EqualWithCapsFormModel,
): EqualWithCapsAllocation => ({
  type: 'EQUAL_WITH_CAPS',
  participants: new Set(form.participants),
  capsInCentsByMember: new Map(
    form.participants.flatMap((member): [string, number][] => {
      const maximum = form.maximumAmountsInEuros[member]?.trim() ?? '';
      if (maximum.length === 0) return [];

      const maximumInCents = parseAmountInCents(maximum);
      return maximumInCents === null ? [] : [[member, maximumInCents]];
    }),
  ),
});

const withoutMember = (
  maximumAmountsInEuros: Readonly<Partial<Record<string, string>>>,
  member: string,
): Readonly<Partial<Record<string, string>>> =>
  Object.fromEntries(
    Object.entries(maximumAmountsInEuros).filter(([cappedMember]) => cappedMember !== member),
  );
