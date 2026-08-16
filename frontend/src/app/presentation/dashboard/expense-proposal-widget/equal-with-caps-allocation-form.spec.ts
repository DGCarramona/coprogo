import {
  emptyEqualWithCapsForm,
  setEqualWithCapsMaximum,
  toEqualWithCapsAllocation,
  toggleEqualWithCapsParticipant,
  validateEqualWithCapsForm,
} from './equal-with-caps-allocation-form';

describe('equal with caps allocation form', () => {
  describe('emptyEqualWithCapsForm', () => {
    it('starts empty', () => {
      expect(emptyEqualWithCapsForm()).toEqual({
        participants: [],
        maximumAmountsInEuros: {},
      });
    });
  });

  describe('toggleEqualWithCapsParticipant', () => {
    it('toggles participants immutably and removes their maximum', () => {
      const empty = emptyEqualWithCapsForm();
      const selected = toggleEqualWithCapsParticipant(empty, 'bob@example.com');
      const capped = setEqualWithCapsMaximum(selected, 'bob@example.com', '25,50');

      expect(toggleEqualWithCapsParticipant(capped, 'bob@example.com')).toEqual(
        emptyEqualWithCapsForm(),
      );
      expect(empty).toEqual(emptyEqualWithCapsForm());
      expect(selected.maximumAmountsInEuros).toEqual({});
    });
  });

  describe('setEqualWithCapsMaximum', () => {
    it('sets and clears a selected participant maximum without mutating the source', () => {
      const selected = toggleEqualWithCapsParticipant(emptyEqualWithCapsForm(), 'bob@example.com');
      const capped = setEqualWithCapsMaximum(selected, 'bob@example.com', '25,50');

      expect(capped.maximumAmountsInEuros).toEqual({ 'bob@example.com': '25,50' });
      expect(setEqualWithCapsMaximum(capped, 'bob@example.com', ' ')).toEqual(selected);
      expect(setEqualWithCapsMaximum(capped, 'alice@example.com', '10')).toBe(capped);
      expect(selected.maximumAmountsInEuros).toEqual({});
    });
  });

  describe('validateEqualWithCapsForm', () => {
    it('validates participants, amounts and the required uncapped participant', () => {
      expect(validateEqualWithCapsForm(emptyEqualWithCapsForm())).toEqual({
        kind: 'participants',
        message: 'Choisissez au moins un participant.',
      });

      const participants = ['alice@example.com', 'bob@example.com'];
      expect(
        validateEqualWithCapsForm({
          participants,
          maximumAmountsInEuros: { 'bob@example.com': '25,555' },
        }),
      ).toEqual({
        kind: 'maximum-amount',
        message: 'Indiquez un montant maximum positif avec deux decimales au plus.',
      });
      expect(
        validateEqualWithCapsForm({
          participants,
          maximumAmountsInEuros: {
            'alice@example.com': '50',
            'bob@example.com': '25,50',
          },
        }),
      ).toEqual({
        kind: 'uncapped-participant',
        message: 'Laissez au moins un participant sans montant maximum.',
      });
      expect(
        validateEqualWithCapsForm({
          participants,
          maximumAmountsInEuros: { 'bob@example.com': '25,50' },
        }),
      ).toBeUndefined();
    });
  });

  describe('toEqualWithCapsAllocation', () => {
    it('builds the application allocation', () => {
      expect(
        toEqualWithCapsAllocation({
          participants: ['alice@example.com', 'bob@example.com'],
          maximumAmountsInEuros: { 'bob@example.com': '25,50' },
        }),
      ).toEqual({
        type: 'EQUAL_WITH_CAPS',
        participants: new Set(['alice@example.com', 'bob@example.com']),
        capsInCentsByMember: new Map([['bob@example.com', 2550]]),
      });
    });
  });
});
