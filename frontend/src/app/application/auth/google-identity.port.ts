export interface RenderGoogleSignInButtonCommand {
  host: HTMLElement;
  locale: string;
  onCredential: (idToken: string) => void | Promise<void>;
}

export abstract class GoogleIdentityPort {
  abstract isConfigured(): boolean;

  abstract renderButton(command: RenderGoogleSignInButtonCommand): void;
}
