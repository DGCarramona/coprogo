import { Routes } from '@angular/router';

import { authenticatedRouteGuard } from './authenticated-route.guard';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'invitations',
  },
  {
    path: 'connexion',
    loadComponent: () =>
      import('./presentation/auth/sign-in-page.component').then(
        (module) => module.SignInPageComponent,
      ),
  },
  {
    path: 'invitations',
    canActivate: [authenticatedRouteGuard],
    loadComponent: () =>
      import('./presentation/invitations/pending-invitations-page.component').then(
        (module) => module.PendingInvitationsPageComponent,
      ),
  },
  {
    path: 'groups/:groupId/dashboard',
    canActivate: [authenticatedRouteGuard],
    loadComponent: () =>
      import('./presentation/dashboard/group-dashboard-page.component').then(
        (module) => module.GroupDashboardPageComponent,
      ),
  },
  {
    path: 'groups/:groupId/reimbursements',
    canActivate: [authenticatedRouteGuard],
    loadComponent: () =>
      import('./presentation/reimbursement/reimbursement-history-page.component').then(
        (module) => module.ReimbursementHistoryPageComponent,
      ),
  },
  {
    path: 'groups/:groupId/ownership-shares',
    canActivate: [authenticatedRouteGuard],
    loadComponent: () =>
      import('./presentation/revenue/ownership-share-timeline-page.component').then(
        (module) => module.OwnershipShareTimelinePageComponent,
      ),
  },
  {
    path: 'groups/:groupId/revenue-distribution/preview',
    canActivate: [authenticatedRouteGuard],
    loadComponent: () =>
      import('./presentation/revenue/revenue-distribution-preview-page.component').then(
        (module) => module.RevenueDistributionPreviewPageComponent,
      ),
  },
  {
    path: 'groups/:groupId/expenses/:expenseId',
    canActivate: [authenticatedRouteGuard],
    loadComponent: () =>
      import('./presentation/expense/expense-detail-page.component').then(
        (module) => module.ExpenseDetailPageComponent,
      ),
  },
  {
    path: 'groups/:groupId/expenses',
    canActivate: [authenticatedRouteGuard],
    loadComponent: () =>
      import('./presentation/expense/expense-list-page.component').then(
        (module) => module.ExpenseListPageComponent,
      ),
  },
  {
    path: '**',
    redirectTo: 'invitations',
  },
];
