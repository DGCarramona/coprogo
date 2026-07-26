import { describeError } from './describe-error';

describe('describeError', () => {
  it('uses the error message when available', () => {
    expect(describeError(new Error('Precise failure'), 'Fallback')).toBe('Precise failure');
  });

  it('uses the fallback for blank or non-error values', () => {
    expect(describeError(new Error('   '), 'Fallback')).toBe('Fallback');
    expect(describeError('failure', 'Fallback')).toBe('Fallback');
  });
});
