import { parseGroupName } from './group-name';

describe('parseGroupName', () => {
  it.each([
    [' Appartement familial ', 'Appartement familial'],
    ['a'.repeat(100), 'a'.repeat(100)],
    ['', null],
    ['  \t\n', null],
    ['a'.repeat(101), null],
  ])('normalizes or rejects %j', (raw, expected) => {
    expect(parseGroupName(raw)).toBe(expected);
  });
});
