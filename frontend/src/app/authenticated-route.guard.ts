import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthSessionFacade } from './application/auth/auth-session.facade';

export const authenticatedRouteGuard: CanActivateFn = () => {
  const authSessionFacade = inject(AuthSessionFacade);
  const router = inject(Router);

  return authSessionFacade.hasStoredToken() ? true : router.createUrlTree(['/connexion']);
};
