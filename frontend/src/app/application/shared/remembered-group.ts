const REMEMBERED_GROUP_ID_KEY = 'coprogo.remembered-group-id';

export const rememberGroup = (groupId: string): void => {
  if (typeof sessionStorage !== 'undefined') {
    sessionStorage.setItem(REMEMBERED_GROUP_ID_KEY, groupId);
  }
};

export const getRememberedGroupId = (): string | null => {
  if (typeof sessionStorage === 'undefined') return null;
  return sessionStorage.getItem(REMEMBERED_GROUP_ID_KEY);
};

export const forgetGroup = (): void => {
  if (typeof sessionStorage !== 'undefined') {
    sessionStorage.removeItem(REMEMBERED_GROUP_ID_KEY);
  }
};
