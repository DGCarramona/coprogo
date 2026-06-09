export abstract class GoogleIdTokenPort {
  abstract currentToken(): string | null;

  abstract store(token: string): void;

  abstract clear(): void;
}
