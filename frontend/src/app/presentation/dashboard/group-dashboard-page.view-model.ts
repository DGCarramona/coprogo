import { Injectable, computed, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { AuthSessionFacade } from '../../application/auth/auth-session.facade';
import { GroupFinancialDashboardPort } from '../../application/ledger/group-financial-dashboard.port';
import { NavigationPort } from '../../application/shared/navigation.port';
import { describeError } from '../../application/shared/describe-error';
import { GroupFinancialDashboard } from '../../domain/ledger/group-financial-dashboard';
import { formatMoneyFromCents, formatSignedMoneyFromCents } from '../../shared/format/financial-format';

type DashboardStatus = 'idle' | 'loading' | 'ready' | 'failed';

export interface MemberBalanceViewItem {
  readonly member: string;
  readonly label: string;
  readonly amount: string;
  readonly tone: 'credit' | 'debt' | 'neutral';
}

export interface CashPoolShareViewItem {
  readonly member: string;
  readonly amount: string;
}

@Injectable()
export class GroupDashboardPageViewModel {
  private readonly statusState = signal<DashboardStatus>('idle');
  private readonly dashboardState = signal<GroupFinancialDashboard | null>(null);
  private readonly errorMessageState = signal<string | null>(null);

  constructor(
    private readonly route: ActivatedRoute,
    private readonly navigation: NavigationPort,
    private readonly authSessionFacade: AuthSessionFacade,
    private readonly dashboardPort: GroupFinancialDashboardPort,
  ) {}

  readonly status = this.statusState.asReadonly();
  readonly errorMessage = this.errorMessageState.asReadonly();
  readonly groupId = computed(() => this.dashboardState()?.groupId ?? this.route.snapshot.paramMap.get('groupId'));
  readonly isLoading = computed(() => this.status() === 'loading');
  readonly hasLoadError = computed(() => this.status() === 'failed');
  readonly isReady = computed(() => this.status() === 'ready');
  readonly cashPoolBalance = computed(() =>
    formatMoneyFromCents(this.dashboardState()?.cashPoolBalance.availableAmountInCents ?? 0),
  );
  readonly memberBalances = computed<readonly MemberBalanceViewItem[]>(() =>
    this.dashboardState()?.memberBalances.map((balance) => ({
      member: balance.member,
      label: balance.netAmountInCents >= 0 ? 'A recevoir' : 'A payer',
      amount: formatSignedMoneyFromCents(balance.netAmountInCents),
      tone:
        balance.netAmountInCents > 0 ? 'credit' : balance.netAmountInCents < 0 ? 'debt' : 'neutral',
    })) ?? [],
  );
  readonly cashPoolShares = computed<readonly CashPoolShareViewItem[]>(() =>
    this.dashboardState()?.cashPoolShares.map((share) => ({
      member: share.member,
      amount: formatMoneyFromCents(share.amountInCents),
    })) ?? [],
  );

  async initialize(): Promise<void> {
    if (!this.authSessionFacade.hasStoredToken()) {
      await this.navigation.navigateByUrl('/connexion');
      return;
    }

    await this.load();
  }

  async retry(): Promise<void> {
    await this.load();
  }

  private async load(): Promise<void> {
    const groupId = this.route.snapshot.paramMap.get('groupId');
    if (!groupId) {
      this.statusState.set('failed');
      this.errorMessageState.set('Aucun groupe n est selectionne.');
      return;
    }

    this.statusState.set('loading');
    this.errorMessageState.set(null);

    try {
      this.dashboardState.set(await this.dashboardPort.get(groupId));
      this.statusState.set('ready');
    } catch (error) {
      this.dashboardState.set(null);
      this.statusState.set('failed');
      this.errorMessageState.set(describeError(error, 'Le dashboard financier n a pas pu etre charge.'));
    }
  }
}
