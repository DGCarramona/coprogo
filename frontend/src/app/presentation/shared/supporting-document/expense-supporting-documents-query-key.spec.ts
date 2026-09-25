import { expenseSupportingDocumentsQueryKey } from './expense-supporting-documents-query-key';

describe('expenseSupportingDocumentsQueryKey', () => {
  it('creates a stable key scoped to one group expense', () => {
    expect(expenseSupportingDocumentsQueryKey('group-1', 'expense-1')).toEqual([
      'groups',
      'group-1',
      'expenses',
      'expense-1',
      'supporting-documents',
    ]);
  });
});
