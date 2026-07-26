import { ActivatedRoute, convertToParamMap } from '@angular/router';

import { AuthSessionFacade } from '../../application/auth/auth-session.facade';
import { GroupFinancialDashboardPort } from '../../application/ledger/group-financial-dashboard.port';
import {
  authSessionFacadeWithToken,
  defaultAuthSessionFacade,
} from '../../../../__test__/app/application/auth/stub-google-id-token.port';
import { StubNavigationPort } from '../../../../__test__/app/application/shared/stub-navigation.port';
import { GroupDashboardPageViewModel } from './group-dashboard-page.view-model';

describe('GroupDashboardPageViewModel', () => {
  it('loads the group financial dashboard', async () => {
    const dashboardPort = new StubGroupFinancialDashboardPort();
    const viewModel = createViewModel({ dashboardPort });

    await viewModel.initialize();

    expect(viewModel.status()).toBe('ready');
    expect(viewModel.groupId()).toBe('group-1');
    expect(viewModel.cashPoolBalance()).toBe('75,00\u00a0€');
    expect(viewModel.memberBalances()).toEqual([
      { member: 'alice@example.com', label: 'A recevoir', amount: '+40,00\u00a0€', tone: 'credit' },
      { member: 'bob@example.com', label: 'A payer', amount: '-40,00\u00a0€', tone: 'debt' },
    ]);
    expect(viewModel.cashPoolShares()).toEqual([
      { member: 'alice@example.com', amount: '35,00\u00a0€' },
      { member: 'bob@example.com', amount: '40,00\u00a0€' },
    ]);
  });

  it('redirects to sign-in when no token is stored', async () => {
    const navigation = new StubNavigationPort();
    const viewModel = createViewModel({
      authSessionFacade: authSessionFacadeWithToken(false),
      navigation,
    });

    await viewModel.initialize();

    expect(navigation.navigatedTo).toBe('/connexion');
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

const defaultNavigation = new StubNavigationPort();

const createViewModel = ({
  authSessionFacade = defaultAuthSessionFacade,
  dashboardPort = new StubGroupFinancialDashboardPort(),
  navigation = defaultNavigation,
}: {
  authSessionFacade?: AuthSessionFacade;
  dashboardPort?: StubGroupFinancialDashboardPort;
  navigation?: StubNavigationPort;
} = {}): GroupDashboardPageViewModel => {
  return new GroupDashboardPageViewModel(
    { snapshot: { paramMap: convertToParamMap({ groupId: 'group-1' }) } } as ActivatedRoute,
    navigation,
    authSessionFacade,
    dashboardPort,
  );
};

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
