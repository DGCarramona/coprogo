import {
  addCumulativeIntermediateTier,
  emptyCumulativeTiersForm,
  removeCumulativeIntermediateTier,
  setCumulativeIntermediateThreshold,
  toCumulativeTiersAllocation,
  toggleCumulativeFinalParticipant,
  toggleCumulativeIntermediateParticipant,
  validateCumulativeTiersForm,
} from './cumulative-tiers-allocation-form';

describe('cumulative tiers allocation form', () => {
  it('starts with an implicit empty final tier', () => {
    expect(emptyCumulativeTiersForm()).toEqual({
      intermediateTiers: [],
      finalParticipants: [],
    });
  });

  it('adds, edits and removes intermediate tiers immutably', () => {
    const empty = emptyCumulativeTiersForm();
    const added = addCumulativeIntermediateTier(empty);
    const threshold = setCumulativeIntermediateThreshold(added, 0, '40');
    const participant = toggleCumulativeIntermediateParticipant(threshold, 0, 'alice@example.com');

    expect(participant.intermediateTiers).toEqual([
      { upToAmountInEuros: '40', participants: ['alice@example.com'] },
    ]);
    expect(removeCumulativeIntermediateTier(participant, 0)).toEqual(empty);
    expect(empty).toEqual(emptyCumulativeTiersForm());
  });

  it('ignores invalid intermediate tier indexes', () => {
    const form = addCumulativeIntermediateTier(emptyCumulativeTiersForm());

    expect(removeCumulativeIntermediateTier(form, 2)).toBe(form);
    expect(setCumulativeIntermediateThreshold(form, -1, '40')).toBe(form);
    expect(toggleCumulativeIntermediateParticipant(form, 3, 'alice@example.com')).toBe(form);
  });

  it('toggles final participants immutably', () => {
    const empty = emptyCumulativeTiersForm();
    const selected = toggleCumulativeFinalParticipant(empty, 'alice@example.com');

    expect(selected.finalParticipants).toEqual(['alice@example.com']);
    expect(toggleCumulativeFinalParticipant(selected, 'alice@example.com')).toEqual(empty);
    expect(empty.finalParticipants).toEqual([]);
  });

  it('requires participants in every tier', () => {
    expect(validateCumulativeTiersForm(emptyCumulativeTiersForm(), 10_100)).toEqual({
      kind: 'tier-participants',
      message: 'Choisissez au moins un participant pour chaque tranche.',
    });
  });

  it.each([['40,555'], ['0'], ['-1']])('rejects the invalid threshold %s', (threshold) => {
    expect(validateCumulativeTiersForm(formWithThresholds([threshold]), 10_100)).toEqual({
      kind: 'tier-threshold',
      message: 'Indiquez des seuils positifs avec deux decimales au plus.',
    });
  });

  it.each([
    [['40', '40'], 10_100],
    [['101'], 10_100],
    [['102'], 10_100],
  ])('requires increasing thresholds below the total', (thresholds, totalAmountInCents) => {
    expect(validateCumulativeTiersForm(formWithThresholds(thresholds), totalAmountInCents)).toEqual(
      {
        kind: 'tier-order',
        message: 'Les seuils doivent augmenter et rester inferieurs au montant total.',
      },
    );
  });

  it('requires every participant to take part for at least one cent per tier', () => {
    expect(
      validateCumulativeTiersForm(
        {
          intermediateTiers: [],
          finalParticipants: ['alice@example.com', 'bob@example.com'],
        },
        1,
      ),
    ).toEqual({
      kind: 'tier-share',
      message: 'Chaque participant doit participer pour au moins un centime dans chaque tranche.',
    });
  });

  it('accepts valid tiers and builds the application allocation', () => {
    const form = {
      intermediateTiers: [
        {
          upToAmountInEuros: '40',
          participants: ['alice@example.com', 'bob@example.com'],
        },
      ],
      finalParticipants: ['alice@example.com'],
    };

    expect(validateCumulativeTiersForm(form, 10_100)).toBeUndefined();
    expect(toCumulativeTiersAllocation(form, 10_100)).toEqual({
      type: 'CUMULATIVE_TIERS',
      tiers: [
        {
          upToAmountInCents: 4000,
          participants: new Set(['alice@example.com', 'bob@example.com']),
        },
        {
          upToAmountInCents: 10_100,
          participants: new Set(['alice@example.com']),
        },
      ],
    });
  });
});

const formWithThresholds = (thresholds: readonly string[]) => ({
  intermediateTiers: thresholds.map((upToAmountInEuros) => ({
    upToAmountInEuros,
    participants: ['alice@example.com'],
  })),
  finalParticipants: ['bob@example.com'],
});
