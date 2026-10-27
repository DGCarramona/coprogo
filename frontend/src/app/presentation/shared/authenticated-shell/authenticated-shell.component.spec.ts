import { TestBed } from '@angular/core/testing';
import { QueryClient } from '@tanstack/angular-query-experimental';
import { AuthSessionFacade } from '../../../application/auth/auth-session.facade';
import { NavigationPort } from '../../../application/shared/navigation.port';
import { authSessionFacadeWithToken } from '../../../../../__test__/app/application/auth/stub-google-id-token.port';
import { StubNavigationPort } from '../../../../../__test__/app/application/shared/stub-navigation.port';
import { AuthenticatedShellComponent } from './authenticated-shell.component';

describe('AuthenticatedShellComponent', () => {
  it('clears private cached data when signing out before another member signs in', async () => {
    const queryClient = new QueryClient();
    queryClient.setQueryData(['member-groups'], [{ groupId: 'private-group' }]);
    const auth = authSessionFacadeWithToken(true);
    const navigation = new StubNavigationPort();
    TestBed.configureTestingModule({
      providers: [
        { provide: QueryClient, useValue: queryClient },
        { provide: AuthSessionFacade, useValue: auth },
        { provide: NavigationPort, useValue: navigation },
      ],
    });
    const fixture = TestBed.createComponent(AuthenticatedShellComponent);
    fixture.detectChanges();
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('button')!.click();
    await fixture.whenStable();

    expect(queryClient.getQueryData(['member-groups'])).toBeUndefined();
    expect(auth.hasStoredToken()).toBe(false);
    expect(navigation.navigatedTo).toBe('/connexion');
  });
});
