import { AuthSessionFacade } from '../../../../src/app/application/auth/auth-session.facade';
import { GoogleIdTokenPort } from '../../../../src/app/application/auth/google-id-token.port';

export class StubGoogleIdTokenPort extends GoogleIdTokenPort {
  private token: string | null = null;

  currentToken(): string | null {
    return this.token;
  }

  store(token: string): void {
    this.token = token;
  }

  clear(): void {
    this.token = null;
  }
}

export const authSessionFacadeWithToken = (hasToken: boolean): AuthSessionFacade => {
  const port = new StubGoogleIdTokenPort();
  if (hasToken) port.store('test-token');
  return new AuthSessionFacade(port);
};

export const defaultAuthSessionFacade = authSessionFacadeWithToken(true);
