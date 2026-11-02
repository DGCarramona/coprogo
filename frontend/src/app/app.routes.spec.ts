import { routes } from './app.routes';

describe('routes', () => {
  it('matches the group dashboard directly from its URL', () => {
    const route = routes.find((candidate) => candidate.path === 'groups/:groupId/dashboard');

    expect(route).toBeDefined();
    expect(route?.canActivate).toHaveLength(1);
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
