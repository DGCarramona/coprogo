export const parseLocalDateTime = (value: string): Date | null => {
  if (value.trim().length === 0) return null;

  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
};
