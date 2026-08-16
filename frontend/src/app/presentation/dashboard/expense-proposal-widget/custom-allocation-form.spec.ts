import {
  emptyCustomAllocationForm,
  setCustomAmount,
  toCustomAllocation,
  validateCustomAllocationForm,
} from './custom-allocation-form';

describe('custom allocation form', () => {
  describe('emptyCustomAllocationForm', () => {
    it('starts without any participation amount', () => {
      expect(emptyCustomAllocationForm()).toEqual({ amountsInEurosByMember: {} });
    });
  });

  describe('setCustomAmount', () => {
    it('sets and removes an amount immutably', () => {
      const empty = emptyCustomAllocationForm();
      const filled = setCustomAmount(empty, 'alice@example.com', '25,50');

      expect(filled).toEqual({
        amountsInEurosByMember: { 'alice@example.com': '25,50' },
      });
      expect(setCustomAmount(filled, 'alice@example.com', ' ')).toEqual(empty);
      expect(empty).toEqual(emptyCustomAllocationForm());
    });
  });

  describe('validateCustomAllocationForm', () => {
    it('requires at least one participation', () => {
      expect(validateCustomAllocationForm(emptyCustomAllocationForm(), 1000)).toEqual({
        kind: 'participations',
        message: 'Indiquez un montant pour au moins un participant.',
      });
    });

    it.each(['0', '-1', '10,555', 'not-an-amount'])(
      'rejects the invalid amount %s',
      (amountInEuros) => {
        expect(
          validateCustomAllocationForm(
            { amountsInEurosByMember: { 'alice@example.com': amountInEuros } },
            1000,
          ),
        ).toEqual({
          kind: 'amount',
          message: 'Indiquez des montants positifs avec deux decimales au plus.',
        });
      },
    );

    it('requires the participation sum to equal the total amount', () => {
      expect(
        validateCustomAllocationForm(
          {
            amountsInEurosByMember: {
              'alice@example.com': '7',
              'bob@example.com': '2,99',
            },
          },
          1000,
        ),
      ).toEqual({
        kind: 'total',
        message: 'La somme des montants doit correspondre au montant total.',
      });
    });

    it('accepts positive participation amounts whose sum equals the total', () => {
      expect(
        validateCustomAllocationForm(
          {
            amountsInEurosByMember: {
              'alice@example.com': '7',
              'bob@example.com': '3,00',
            },
          },
          1000,
        ),
      ).toBeUndefined();
    });
  });

  describe('toCustomAllocation', () => {
    it('builds the application allocation', () => {
      expect(
        toCustomAllocation({
          amountsInEurosByMember: {
            'alice@example.com': '7',
            'bob@example.com': '3,00',
          },
        }),
      ).toEqual({
        type: 'CUSTOM',
        amountsInCentsByMember: new Map([
          ['alice@example.com', 700],
          ['bob@example.com', 300],
        ]),
      });
    });
  });
});
