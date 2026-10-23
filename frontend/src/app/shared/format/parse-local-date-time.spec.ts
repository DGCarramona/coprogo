import { parseLocalDateTime } from './parse-local-date-time';

describe('parseLocalDateTime', () => {
  it('parses a local date and time', () => {
    expect(parseLocalDateTime('2026-10-03T09:30')).toEqual(new Date('2026-10-03T09:30'));
  });

  it.each(['', '   ', 'date-invalide'])('rejects %s', (value) => {
    expect(parseLocalDateTime(value)).toBeNull();
  });
});
