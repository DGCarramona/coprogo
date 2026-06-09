import { HttpErrorResponse } from '@angular/common/http';

export class ApiClientError extends Error {
  constructor(
    message: string,
    readonly status?: number,
  ) {
    super(message);
  }
}

interface ApiErrorResponse {
  message?: string;
}

export const toApiClientError = (error: unknown, fallbackMessage: string): ApiClientError => {
  if (!(error instanceof HttpErrorResponse)) {
    return new ApiClientError(fallbackMessage);
  }

  const apiMessage =
    isApiErrorResponse(error.error) && typeof error.error.message === 'string'
      ? error.error.message
      : null;

  return new ApiClientError(apiMessage ?? fallbackMessage, error.status);
};

const isApiErrorResponse = (error: unknown): error is ApiErrorResponse =>
  typeof error === 'object' && error !== null;
