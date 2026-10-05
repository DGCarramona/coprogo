import { toggleMember } from './expense-proposal-form';

describe('toggleMember', () => {
  it('adds or removes a member without mutating the source', () => {
    const members = ['alice@example.com'];

    expect(toggleMember(members, 'bob@example.com')).toEqual([
      'alice@example.com',
      'bob@example.com',
    ]);
    expect(toggleMember(members, 'alice@example.com')).toEqual([]);
    expect(members).toEqual(['alice@example.com']);
  });
});
