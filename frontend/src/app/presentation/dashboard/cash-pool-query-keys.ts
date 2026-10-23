export const cashPoolHistoryQueryKey = (groupId: string | null) =>
  ['groups', groupId, 'cash-pool', 'history'] as const;
