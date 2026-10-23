import { toExpenseCreationDate, toExpenseStatus } from './expense-response.mapper';

describe('expense response mapper', () => {
  describe('toExpenseStatus', () => {
    it.each(['PROPOSED', 'ACCEPTED', 'INVALIDATED'] as const)('maps the %s status', (status) => {
      expect(toExpenseStatus(status)).toBe(status);
    });

    it('rejects an unknown status', () => {
      expect(() => toExpenseStatus('ARCHIVED')).toThrow('Statut de depense inconnu: ARCHIVED.');
    });
  });

  describe('toExpenseCreationDate', () => {
    it('maps an ISO date', () => {
      expect(toExpenseCreationDate('2026-07-01T10:00:00Z')).toEqual(
        new Date('2026-07-01T10:00:00Z'),
      );
    });

    it('rejects an invalid date', () => {
      expect(() => toExpenseCreationDate('date-invalide')).toThrow(
        'Date de creation de depense invalide: date-invalide.',
      );
    });
  });
});
