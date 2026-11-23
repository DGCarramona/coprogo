import { parseLocalDateTime } from './parse-local-date-time';

describe('parseLocalDateTime', () => {
  it.each([
    '2026-04-01T00:00',
    '2026-03-29T01:30',
    '2026-03-29T03:30',
    '2026-10-25T01:30',
    '2026-10-25T03:30',
    '2026-10-03T09:30:45.123',
  ])('interprets %s in the user timezone', (value) => {
    expect(parseLocalDateTime(value)?.toISOString()).toBe(new Date(value).toISOString());
  });

  it.each([
    ['2026-04-01T00:00:00Z', '2026-04-01T00:00:00.000Z'],
    ['2026-04-01T00:00:00+02:00', '2026-03-31T22:00:00.000Z'],
  ])('preserves the explicit timezone of %s', (value, expected) => {
    expect(parseLocalDateTime(value)?.toISOString()).toBe(expected);
  });

  it.each(['', '   ', 'date-invalide', '2026-04-01T25:00', '2026-13-01T00:00'])(
    'rejects %s',
    (value) => {
      expect(parseLocalDateTime(value)).toBeNull();
    },
  );
});
