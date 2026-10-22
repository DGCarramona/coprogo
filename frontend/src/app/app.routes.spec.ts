import { routes } from './app.routes';

describe('routes', () => {
  it('exposes the expense list within its group', () => {
    expect(routes.map((route) => route.path)).toContain('groups/:groupId/expenses');
  });

  it('exposes an expense detail within its group', () => {
    expect(routes.map((route) => route.path)).toContain('groups/:groupId/expenses/:expenseId');
  });

  it('exposes the reimbursement history within its group', () => {
    expect(routes.map((route) => route.path)).toContain('groups/:groupId/reimbursements');
  });
});
