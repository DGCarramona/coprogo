import type { ExpenseAllocation } from '../../../application/expense/expense-proposal.port';
import {
  AllocationValidationError,
  parseAmountInCents,
  toggleMember,
} from './expense-proposal-form';

interface CumulativeIntermediateTierFormModel {
  readonly upToAmountInEuros: string;
  readonly participants: readonly string[];
}

export interface CumulativeTiersFormModel {
  readonly intermediateTiers: readonly CumulativeIntermediateTierFormModel[];
  readonly finalParticipants: readonly string[];
}

type CumulativeTiersAllocation = Extract<ExpenseAllocation, { type: 'CUMULATIVE_TIERS' }>;

export const emptyCumulativeTiersForm = (): CumulativeTiersFormModel => ({
  intermediateTiers: [],
  finalParticipants: [],
});

export const addCumulativeIntermediateTier = (
  form: CumulativeTiersFormModel,
): CumulativeTiersFormModel => ({
  ...form,
  intermediateTiers: [...form.intermediateTiers, { upToAmountInEuros: '', participants: [] }],
});

export const removeCumulativeIntermediateTier = (
  form: CumulativeTiersFormModel,
  index: number,
): CumulativeTiersFormModel =>
  hasIndex(form.intermediateTiers, index)
    ? {
        ...form,
        intermediateTiers: form.intermediateTiers.filter((_, tierIndex) => tierIndex !== index),
      }
    : form;

export const setCumulativeIntermediateThreshold = (
  form: CumulativeTiersFormModel,
  index: number,
  upToAmountInEuros: string,
): CumulativeTiersFormModel =>
  hasIndex(form.intermediateTiers, index)
    ? {
        ...form,
        intermediateTiers: form.intermediateTiers.map((tier, tierIndex) =>
          tierIndex === index ? { ...tier, upToAmountInEuros } : tier,
        ),
      }
    : form;

export const toggleCumulativeIntermediateParticipant = (
  form: CumulativeTiersFormModel,
  index: number,
  member: string,
): CumulativeTiersFormModel =>
  hasIndex(form.intermediateTiers, index)
    ? {
        ...form,
        intermediateTiers: form.intermediateTiers.map((tier, tierIndex) =>
          tierIndex === index
            ? { ...tier, participants: toggleMember(tier.participants, member) }
            : tier,
        ),
      }
    : form;

export const toggleCumulativeFinalParticipant = (
  form: CumulativeTiersFormModel,
  member: string,
): CumulativeTiersFormModel => ({
  ...form,
  finalParticipants: toggleMember(form.finalParticipants, member),
});

export const validateCumulativeTiersForm = (
  form: CumulativeTiersFormModel,
  totalAmountInCents: number,
): AllocationValidationError | undefined => {
  if (
    form.intermediateTiers.some((tier) => tier.participants.length === 0) ||
    form.finalParticipants.length === 0
  ) {
    return {
      kind: 'tier-participants',
      message: 'Choisissez au moins un participant pour chaque tranche.',
    };
  }

  const parsedThresholds = form.intermediateTiers.map((tier) =>
    parseAmountInCents(tier.upToAmountInEuros),
  );
  if (parsedThresholds.some((threshold) => threshold === null)) {
    return {
      kind: 'tier-threshold',
      message: 'Indiquez des seuils positifs avec deux decimales au plus.',
    };
  }

  const thresholds = parsedThresholds.filter(
    (threshold): threshold is number => threshold !== null,
  );
  const hasInvalidOrder = thresholds.some(
    (threshold, index) =>
      threshold >= totalAmountInCents || (index > 0 && threshold <= thresholds[index - 1]),
  );
  if (hasInvalidOrder) {
    return {
      kind: 'tier-order',
      message: 'Les seuils doivent augmenter et rester inferieurs au montant total.',
    };
  }

  const boundaries = [0, ...thresholds, totalAmountInCents];
  const participantCounts = [
    ...form.intermediateTiers.map((tier) => tier.participants.length),
    form.finalParticipants.length,
  ];
  const hasZeroCentShare = participantCounts.some(
    (participantCount, index) => boundaries[index + 1] - boundaries[index] < participantCount,
  );
  return hasZeroCentShare
    ? {
        kind: 'tier-share',
        message: 'Chaque participant doit participer pour au moins un centime dans chaque tranche.',
      }
    : undefined;
};

export const toCumulativeTiersAllocation = (
  form: CumulativeTiersFormModel,
  totalAmountInCents: number,
): CumulativeTiersAllocation => ({
  type: 'CUMULATIVE_TIERS',
  tiers: [
    ...form.intermediateTiers.flatMap((tier): CumulativeTiersAllocation['tiers'] => {
      const upToAmountInCents = parseAmountInCents(tier.upToAmountInEuros);
      return upToAmountInCents === null
        ? []
        : [{ upToAmountInCents, participants: new Set(tier.participants) }];
    }),
    {
      upToAmountInCents: totalAmountInCents,
      participants: new Set(form.finalParticipants),
    },
  ],
});

const hasIndex = <T>(values: readonly T[], index: number): boolean =>
  index >= 0 && index < values.length;
