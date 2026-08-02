import { TestBed } from '@angular/core/testing';
import { QueryClient } from '@tanstack/angular-query-experimental';

import { ExpenseProposalPort } from './application/expense/expense-proposal.port';
import { GroupMembersPort } from './application/group/group-members.port';
import { NavigationPort } from './application/shared/navigation.port';
import { HttpExpenseProposalGateway } from './infrastructure/expense/http-expense-proposal.gateway';
import { HttpGroupMembersGateway } from './infrastructure/group/http-group-members.gateway';
import { RouterNavigationAdapter } from './infrastructure/router/router-navigation.adapter';
import { appConfig } from './app.config';

describe('appConfig', () => {
  it('provides a TanStack Query client', () => {
    TestBed.configureTestingModule({ providers: appConfig.providers });

    expect(TestBed.inject(QueryClient)).toBeInstanceOf(QueryClient);
  });

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

  it('binds the group members port to the existing HTTP gateway instance', () => {
    TestBed.configureTestingModule({ providers: appConfig.providers });

    const groupMembersPort = TestBed.inject(GroupMembersPort);

    expect(groupMembersPort).toBeInstanceOf(HttpGroupMembersGateway);
    expect(groupMembersPort).toBe(TestBed.inject(HttpGroupMembersGateway));
  });
});
