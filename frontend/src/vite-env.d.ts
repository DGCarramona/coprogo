/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly APP_API_BASE_URL?: string;
  readonly APP_GOOGLE_CLIENT_ID?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

interface GoogleIdentityCredentialResponse {
  credential: string;
}

interface GoogleIdentityButtonConfiguration {
  theme?: 'outline' | 'filled_blue' | 'filled_black';
  size?: 'large' | 'medium' | 'small';
  text?: 'signin_with' | 'signup_with' | 'continue_with' | 'signin';
  shape?: 'rectangular' | 'pill' | 'circle' | 'square';
  width?: number;
}

interface GoogleIdentityConfiguration {
  client_id: string;
  callback: (response: GoogleIdentityCredentialResponse) => void;
  context?: 'signin' | 'signup' | 'use';
  ux_mode?: 'popup' | 'redirect';
}

interface GoogleIdentityAccountsIdApi {
  initialize(configuration: GoogleIdentityConfiguration): void;
  renderButton(parent: HTMLElement, options: GoogleIdentityButtonConfiguration): void;
}

interface Window {
  google?: {
    accounts: {
      id: GoogleIdentityAccountsIdApi;
    };
  };
}
