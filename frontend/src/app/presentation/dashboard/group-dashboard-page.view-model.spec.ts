import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';

import { AuthSessionFacade } from '../../application/auth/auth-session.facade';
import { GroupFinancialDashboardPort } from '../../application/ledger/group-financial-dashboard.port';
import { GroupDashboardPageViewModel } from './group-dashboard-page.view-model';

describe('GroupDashboardPageViewModel', () => {
  it('loads the group financial dashboard', async () => {
    const dashboardPort = new StubGroupFinancialDashboardPort();
    const viewModel = createViewModel({ dashboardPort });

    await viewModel.initialize();

    expect(viewModel.status()).toBe('ready');
    expect(viewModel.groupId()).toBe('group-1');
    expect(viewModel.cashPoolBalance()).toBe('75,00 €');
    expect(viewModel.memberBalances()).toEqual([
      {
        member: 'alice@example.com',
        label: 'A recevoir',
        amount: '+40,00 €',
        tone: 'credit',
      },
      {
        member: 'bob@example.com',
        label: 'A payer',
        amount: '-40,00 €',
        tone: 'debt',
      },
    ]);
    expect(viewModel.cashPoolShares()).toEqual([
      { member: 'alice@example.com', amount: '35,00 €' },
      { member: 'bob@example.com', amount: '40,00 €' },
    ]);
  });

  it('redirects to sign-in when no token is stored', async () => {
    const router = new StubRouter();
    const viewModel = createViewModel({ authSessionFacade: new StubAuthSessionFacade(false), router });

    await viewModel.initialize();

    expect(router.navigatedTo).toBe('/connexion');
  });

  it('exposes a load error', async () => {
    const dashboardPort = new StubGroupFinancialDashboardPort();
    dashboardPort.failure = new Error('Backend indisponible');
    const viewModel = createViewModel({ dashboardPort });

    await viewModel.initialize();

    expect(viewModel.hasLoadError()).toBe(true);
    expect(viewModel.errorMessage()).toBe('Backend indisponible');
  });
});

const createViewModel = ({
  authSessionFacade = new StubAuthSessionFacade(true),
  dashboardPort = new StubGroupFinancialDashboardPort(),
  router = new StubRouter(),
}: {
  authSessionFacade?: StubAuthSessionFacade;
  dashboardPort?: StubGroupFinancialDashboardPort;
  router?: StubRouter;
} = {}): GroupDashboardPageViewModel => {
  return new GroupDashboardPageViewModel(
    {
      snapshot: {
        paramMap: convertToParamMap({ groupId: 'group-1' }),
      },
    } as ActivatedRoute,
    router as unknown as Router,
    authSessionFacade as unknown as AuthSessionFacade,
    dashboardPort,
  );
};

class StubAuthSessionFacade {
  signedOut = false;

  constructor(private readonly storedToken: boolean) {}

  hasStoredToken(): boolean {
    return this.storedToken;
  }

  signOut(): void {
    this.signedOut = true;
  }
}

class StubGroupFinancialDashboardPort extends GroupFinancialDashboardPort {
  failure: Error | null = null;

  override async get() {
    if (this.failure) {
      throw this.failure;
    }

    return {
      groupId: 'group-1',
      memberBalances: [
        { member: 'alice@example.com', netAmountInCents: 4000 },
        { member: 'bob@example.com', netAmountInCents: -4000 },
      ],
      cashPoolBalance: { availableAmountInCents: 7500 },
      cashPoolShares: [
        { member: 'alice@example.com', amountInCents: 3500 },
        { member: 'bob@example.com', amountInCents: 4000 },
      ],
    };
  }
}

class StubRouter {
  navigatedTo: string | null = null;

  async navigateByUrl(url: string): Promise<boolean> {
    this.navigatedTo = url;
    return true;
  }
}
