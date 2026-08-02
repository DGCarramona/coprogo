import { TestBed } from '@angular/core/testing';

import { ExpenseProposalPort } from './application/expense/expense-proposal.port';
import { NavigationPort } from './application/shared/navigation.port';
import { HttpExpenseProposalGateway } from './infrastructure/expense/http-expense-proposal.gateway';
import { RouterNavigationAdapter } from './infrastructure/router/router-navigation.adapter';
import { appConfig } from './app.config';

describe('appConfig', () => {
  it('binds the expense proposal port to the existing HTTP gateway instance', () => {
    TestBed.configureTestingModule({ providers: appConfig.providers });

    const expenseProposalPort = TestBed.inject(ExpenseProposalPort);

    expect(expenseProposalPort).toBeInstanceOf(HttpExpenseProposalGateway);
    expect(expenseProposalPort).toBe(TestBed.inject(HttpExpenseProposalGateway));
  });

  it('binds the navigation port to the existing router navigation adapter instance', () => {
    TestBed.configureTestingModule({ providers: appConfig.providers });

    const navigationPort = TestBed.inject(NavigationPort);

    expect(navigationPort).toBeInstanceOf(RouterNavigationAdapter);
    expect(navigationPort).toBe(TestBed.inject(RouterNavigationAdapter));
  });
});
