import { authSessionFacadeWithToken } from '../../../../__test__/app/application/auth/stub-google-id-token.port';
import { StubNavigationPort } from '../../../../__test__/app/application/shared/stub-navigation.port';
import {
  GoogleIdentityPort,
  RenderGoogleSignInButtonCommand,
} from '../../application/auth/google-identity.port';
import { SignInPageViewModel } from './sign-in-page.view-model';

class StubGoogleIdentity extends GoogleIdentityPort {
  command: RenderGoogleSignInButtonCommand | null = null;
  override isConfigured(): boolean {
    return true;
  }
  override renderButton(command: RenderGoogleSignInButtonCommand): void {
    this.command = command;
  }
}

describe('SignInPageViewModel', () => {
  describe('initialize', () => {
    it('opens the group list after restoring a session', async () => {
      const navigation = new StubNavigationPort();
      const viewModel = new SignInPageViewModel(
        authSessionFacadeWithToken(true),
        new StubGoogleIdentity(),
        navigation,
      );
      await viewModel.initialize();
      expect(navigation.navigatedTo).toBe('/groups');
    });
  });

  describe('mountGoogleButton', () => {
    it('opens the group list after successful authentication', async () => {
      const navigation = new StubNavigationPort();
      const identity = new StubGoogleIdentity();
      const viewModel = new SignInPageViewModel(
        authSessionFacadeWithToken(false),
        identity,
        navigation,
      );
      viewModel.mountGoogleButton(document.createElement('div'));
      await identity.command!.onCredential('google-token');
      expect(navigation.navigatedTo).toBe('/groups');
    });
  });
});
