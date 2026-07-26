export const describeError = (error: unknown, fallbackMessage: string): string =>
  error instanceof Error && error.message.trim().length > 0 ? error.message : fallbackMessage;
