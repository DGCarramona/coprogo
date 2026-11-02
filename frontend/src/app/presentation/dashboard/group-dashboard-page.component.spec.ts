import { ErrorMessagePipe } from '../i18n/error-message.pipe';
import { TranslatePipe } from '@ngx-translate/core';
import { provideLocalization } from '../i18n/provide-localization';
import { Component, EventEmitter, Input, Output, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { GroupDashboardPageComponent } from './group-dashboard-page.component';
import { GroupDashboardPageViewModel } from './group-dashboard-page.view-model';

@Component({ selector: 'app-authenticated-shell', template: '<ng-content />' })
class StubAuthenticatedShellComponent {
  @Input() eyebrow = '';
  @Input() title = '';
  @Input() description = '';
}

@Component({ selector: 'app-cash-pool-income-widget', template: '' })
class StubCashPoolIncomeWidgetComponent {
  @Input({ required: true }) groupId = '';
  @Output() recorded = new EventEmitter<void>();
}

@Component({ selector: 'app-cash-pool-history-widget', template: '' })
class StubCashPoolHistoryWidgetComponent {
  @Input({ required: true }) groupId = '';
}

@Component({ selector: 'app-cash-pool-withdrawal-widget', template: '' })
class StubCashPoolWithdrawalWidgetComponent {
  @Input({ required: true }) groupId = '';
  @Output() withdrawn = new EventEmitter<void>();
}

@Component({ selector: 'app-expense-list-widget', template: '' })
class StubExpenseListWidgetComponent {
  @Input() groupId = '';
}

@Component({ selector: 'app-expense-proposal-widget', template: '' })
class StubExpenseProposalWidgetComponent {
  @Input({ required: true }) groupId = '';
}

@Component({ selector: 'app-member-debt-reduction-history-widget', template: '' })
class StubMemberDebtReductionHistoryWidgetComponent {
  @Input({ required: true }) groupId = '';
  @Input({ required: true }) member = '';
}

describe('GroupDashboardPageComponent', () => {
  it('organizes the dashboard by financial priority and activity', () => {
    const fixture = createFixture(new StubGroupDashboardPageViewModel());

    fixture.detectChanges();

    expect(
      Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('h2')).map((heading) =>
        heading.textContent?.trim(),
      ),
    ).toEqual([
      'Vue d’ensemble',
      'Gérer la caisse commune',
      'Activité du groupe',
      'Proposer une dépense',
    ]);
  });

  it('shows the expense proposal widget for the ready group', () => {
    const viewModel = new StubGroupDashboardPageViewModel();
    const fixture = createFixture(viewModel);

    fixture.detectChanges();

    const widget = fixture.debugElement.query(By.directive(StubExpenseProposalWidgetComponent));
    if (widget === null) throw new Error('Widget de proposition absent.');

    expect(widget.componentInstance.groupId).toBe('group-1');
  });

  it('does not repeat primary navigation in dashboard shortcuts', () => {
    const fixture = createFixture(new StubGroupDashboardPageViewModel());

    fixture.detectChanges();

    expect(
      (fixture.nativeElement as HTMLElement).querySelector('.group-dashboard-page__quick-links'),
    ).toBeNull();
  });

  it('records an income for the ready group and refreshes its financial amounts', () => {
    const viewModel = new StubGroupDashboardPageViewModel();
    const fixture = createFixture(viewModel);
    fixture.detectChanges();
    const widget = fixture.debugElement.query(By.directive(StubCashPoolIncomeWidgetComponent));

    widget.componentInstance.recorded.emit();

    expect({ groupId: widget.componentInstance.groupId, retryCount: viewModel.retryCount }).toEqual(
      {
        groupId: 'group-1',
        retryCount: 1,
      },
    );
  });

  it('shows the cash-pool history for the ready group', () => {
    const fixture = createFixture(new StubGroupDashboardPageViewModel());

    fixture.detectChanges();

    const widget = fixture.debugElement.query(By.directive(StubCashPoolHistoryWidgetComponent));
    expect(widget.componentInstance.groupId).toBe('group-1');
  });

  it('records a withdrawal for the ready group and refreshes its financial amounts', () => {
    const viewModel = new StubGroupDashboardPageViewModel();
    const fixture = createFixture(viewModel);
    fixture.detectChanges();
    const widget = fixture.debugElement.query(By.directive(StubCashPoolWithdrawalWidgetComponent));

    widget.componentInstance.withdrawn.emit();

    expect({ groupId: widget.componentInstance.groupId, retryCount: viewModel.retryCount }).toEqual(
      {
        groupId: 'group-1',
        retryCount: 1,
      },
    );
  });

  it('offers a financial-operation explanation for each member balance', () => {
    const viewModel = new StubGroupDashboardPageViewModel();
    viewModel.memberBalances.set([
      {
        member: 'alice@example.com',
        label: 'balances.toReceive',
        amount: '+40,00 €',
        tone: 'credit',
      },
      { member: 'bob@example.com', label: 'balances.toPay', amount: '-40,00 €', tone: 'debt' },
    ]);
    const fixture = createFixture(viewModel);

    fixture.detectChanges();

    expect(
      fixture.debugElement
        .queryAll(By.directive(StubMemberDebtReductionHistoryWidgetComponent))
        .map(({ componentInstance }) => ({
          groupId: componentInstance.groupId,
          member: componentInstance.member,
        })),
    ).toEqual([
      { groupId: 'group-1', member: 'alice@example.com' },
      { groupId: 'group-1', member: 'bob@example.com' },
    ]);
  });
});

const createFixture = (
  viewModel: StubGroupDashboardPageViewModel,
): ComponentFixture<GroupDashboardPageComponent> => {
  TestBed.configureTestingModule({
    imports: [GroupDashboardPageComponent],
    providers: [provideLocalization()],
  });
  TestBed.overrideComponent(GroupDashboardPageComponent, {
    set: {
      imports: [
        ErrorMessagePipe,
        TranslatePipe,
        StubAuthenticatedShellComponent,
        StubCashPoolHistoryWidgetComponent,
        StubCashPoolIncomeWidgetComponent,
        StubCashPoolWithdrawalWidgetComponent,
        StubExpenseListWidgetComponent,
        StubExpenseProposalWidgetComponent,
        StubMemberDebtReductionHistoryWidgetComponent,
        MatButtonModule,
        MatCardModule,
        MatChipsModule,
        MatProgressBarModule,
      ],
      providers: [{ provide: GroupDashboardPageViewModel, useValue: viewModel }],
    },
  });

  return TestBed.createComponent(GroupDashboardPageComponent);
};

class StubGroupDashboardPageViewModel {
  readonly groupId = signal('group-1');
  readonly isLoading = signal(false);
  readonly hasLoadError = signal(false);
  readonly isReady = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly cashPoolBalance = signal('0,00\u00a0€');
  readonly memberBalances = signal<
    { member: string; label: string; amount: string; tone: string }[]
  >([]);
  readonly cashPoolShares = signal([]);
  retryCount = 0;

  initialize(): Promise<void> {
    return Promise.resolve();
  }

  retry(): Promise<void> {
    this.retryCount += 1;
    return Promise.resolve();
  }
}
