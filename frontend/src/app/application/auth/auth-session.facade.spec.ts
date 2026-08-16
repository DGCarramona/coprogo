import { AuthSessionFacade } from './auth-session.facade';
import { GoogleIdTokenPort } from './google-id-token.port';

class InMemoryGoogleIdTokenPort extends GoogleIdTokenPort {
  private token: string | null = null;

  override currentToken(): string | null {
    return this.token;
  }

  override store(token: string): void {
    this.token = token;
  }

  override clear(): void {
    this.token = null;
  }
}

describe('AuthSessionFacade', () => {
  const createFacade = (
    overrides: {
      idTokenPort?: GoogleIdTokenPort;
    } = {},
  ): AuthSessionFacade =>
    new AuthSessionFacade(overrides.idTokenPort ?? new InMemoryGoogleIdTokenPort());

  it('stores a Google id token and marks the session ready', async () => {
    const idTokenPort = new InMemoryGoogleIdTokenPort();
    const facade = createFacade({ idTokenPort });

    const authenticated = await facade.authenticateWithGoogleIdToken('google-id-token');

    expect(authenticated).toBe(true);
    expect(idTokenPort.currentToken()).toBe('google-id-token');
    expect(facade.status()).toBe('ready');
  });

  it('restores a stored token as a ready session', async () => {
    const idTokenPort = new InMemoryGoogleIdTokenPort();
    const facade = createFacade({ idTokenPort });
    idTokenPort.store('stored-id-token');

    const restored = await facade.restoreStoredSession();

    expect(restored).toBe(true);
    expect(idTokenPort.currentToken()).toBe('stored-id-token');
    expect(facade.status()).toBe('ready');
  });

  it('does not restore a missing token', async () => {
    const idTokenPort = new InMemoryGoogleIdTokenPort();
    const facade = createFacade({ idTokenPort });

    const restored = await facade.restoreStoredSession();

    expect(restored).toBe(false);
    expect(facade.status()).toBe('signed-out');
  });

  it('clears the token on sign out', async () => {
    const idTokenPort = new InMemoryGoogleIdTokenPort();
    const facade = createFacade({ idTokenPort });
    await facade.authenticateWithGoogleIdToken('google-id-token');

    facade.signOut();

    expect(idTokenPort.currentToken()).toBeNull();
    expect(facade.status()).toBe('signed-out');
  });
});
