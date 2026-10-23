import { formatLocalDate, parseLocalDate } from './local-date';

describe('parseLocalDate', () => {
  it('parses a valid calendar date without changing its local day', () => {
    expect(parseLocalDate('2028-02-29')).toEqual(new Date(2028, 1, 29));
  });

  it.each(['', '2026-02-29', '2026-13-01', '01-10-2026'])('rejects %s', (value) => {
    expect(parseLocalDate(value)).toBeNull();
  });
});

describe('formatLocalDate', () => {
  it('formats the local calendar day for the API', () => {
    expect(formatLocalDate(new Date(2026, 9, 3))).toBe('2026-10-03');
  });
});
