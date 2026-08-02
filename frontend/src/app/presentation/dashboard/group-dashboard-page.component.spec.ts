import { Component, Input, signal } from '@angular/core';
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

@Component({ selector: 'app-expense-list-widget', template: '' })
class StubExpenseListWidgetComponent {
  @Input() groupId = '';
}

@Component({ selector: 'app-expense-proposal-widget', template: '' })
class StubExpenseProposalWidgetComponent {
  @Input({ required: true }) groupId = '';
}

describe('GroupDashboardPageComponent', () => {
  it('shows the expense proposal widget for the ready group', () => {
    const viewModel = new StubGroupDashboardPageViewModel();
    const fixture = createFixture(viewModel);

    fixture.detectChanges();

    const widget = fixture.debugElement.query(By.directive(StubExpenseProposalWidgetComponent));
    if (widget === null) throw new Error('Widget de proposition absent.');

    expect(widget.componentInstance.groupId).toBe('group-1');
  });
});

const createFixture = (
  viewModel: StubGroupDashboardPageViewModel,
): ComponentFixture<GroupDashboardPageComponent> => {
  TestBed.configureTestingModule({
    imports: [GroupDashboardPageComponent],
  });
  TestBed.overrideComponent(GroupDashboardPageComponent, {
    set: {
      imports: [
        StubAuthenticatedShellComponent,
        StubExpenseListWidgetComponent,
        StubExpenseProposalWidgetComponent,
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
  readonly memberBalances = signal([]);
  readonly cashPoolShares = signal([]);

  initialize(): Promise<void> {
    return Promise.resolve();
  }

  retry(): Promise<void> {
    return Promise.resolve();
  }
}
