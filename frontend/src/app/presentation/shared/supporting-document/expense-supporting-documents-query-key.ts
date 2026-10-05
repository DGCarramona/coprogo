export const expenseSupportingDocumentsQueryKey = (groupId: string, expenseId: string) =>
  ['groups', groupId, 'expenses', expenseId, 'supporting-documents'] as const;
