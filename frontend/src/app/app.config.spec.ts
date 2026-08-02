import { TestBed } from '@angular/core/testing';

import { ExpenseProposalPort } from './application/expense/expense-proposal.port';
import { NavigationPort } from './application/shared/navigation.port';
import { HttpExpenseProposalGateway } from './infrastructure/expense/http-expense-proposal.gateway';
import { appConfig } from './app.config';

class StubNavigationPort extends NavigationPort {
  override navigateByUrl(): Promise<boolean> {
    return Promise.resolve(true);
  }
}

describe('appConfig', () => {
  it('binds the expense proposal port to the existing HTTP gateway instance', () => {
    TestBed.configureTestingModule({ providers: appConfig.providers });
    TestBed.overrideProvider(NavigationPort, { useValue: new StubNavigationPort() });

    const expenseProposalPort = TestBed.inject(ExpenseProposalPort);

    expect(expenseProposalPort).toBeInstanceOf(HttpExpenseProposalGateway);
    expect(expenseProposalPort).toBe(TestBed.inject(HttpExpenseProposalGateway));
  });
});
