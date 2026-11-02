import { routes } from './app.routes';
import { forgetGroup, rememberGroup } from './application/shared/remembered-group';

describe('routes', () => {
  afterEach(() => forgetGroup());

  it('restores the most recently opened group from the home route', () => {
    rememberGroup('group-1');
    const redirectTo = routes.find((route) => route.path === '')?.redirectTo;

    expect(typeof redirectTo).toBe('function');
    if (typeof redirectTo !== 'function') throw new Error('Home redirect is not dynamic.');
    expect(redirectTo({} as never)).toBe('/groups/group-1/dashboard');
  });

  it('shows the group selector when no group was previously opened', () => {
    const redirectTo = routes.find((route) => route.path === '')?.redirectTo;

    expect(typeof redirectTo).toBe('function');
    if (typeof redirectTo !== 'function') throw new Error('Home redirect is not dynamic.');
    expect(redirectTo({} as never)).toBe('invitations');
  });
  it('protects and loads the group members page', async () => {
    const route = routes.find((route) => route.path === 'groups/:groupId/members');
    expect(route?.canActivate).toHaveLength(1);
    expect(await route?.loadComponent?.()).toBeDefined();
  });
  it('exposes the expense list within its group', () => {
    expect(routes.map((route) => route.path)).toContain('groups/:groupId/expenses');
  });

  it('exposes an expense detail within its group', () => {
    expect(routes.map((route) => route.path)).toContain('groups/:groupId/expenses/:expenseId');
  });

  it('exposes the reimbursement history within its group', () => {
    expect(routes.map((route) => route.path)).toContain('groups/:groupId/reimbursements');
  });

  it('exposes the ownership-share timeline within its group', () => {
    expect(routes.map((route) => route.path)).toContain('groups/:groupId/ownership-shares');
  });

  it('exposes the revenue-distribution preview within its group', () => {
    expect(routes.map((route) => route.path)).toContain(
      'groups/:groupId/revenue-distribution/preview',
    );
  });
});
