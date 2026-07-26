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
    path: '**',
    redirectTo: 'invitations',
  },
];
