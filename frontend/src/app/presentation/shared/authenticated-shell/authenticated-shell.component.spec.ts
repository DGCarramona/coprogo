import { TranslateService } from '@ngx-translate/core';
import { provideLocalization } from '../../i18n/provide-localization';
import { QueryClient } from '@tanstack/angular-query-experimental';
import { authSessionFacadeWithToken } from '../../../../../__test__/app/application/auth/stub-google-id-token.port';
import { StubNavigationPort } from '../../../../../__test__/app/application/shared/stub-navigation.port';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import { AuthSessionFacade } from '../../../application/auth/auth-session.facade';
import { NavigationPort } from '../../../application/shared/navigation.port';
import { AuthenticatedShellComponent } from './authenticated-shell.component';

describe('AuthenticatedShellComponent', () => {
  it('shows persistent links for the current group and invitations', () => {
    const fixture = createFixture('group-1');

    fixture.detectChanges();

    const links = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLAnchorElement>(
        '.authenticated-shell__navigation a',
      ),
    ).map(({ href }) => new URL(href).pathname);

    expect(links).toEqual([
      '/groups/group-1/dashboard',
      '/groups/group-1/members',
      '/groups/group-1/expenses',
      '/groups/group-1/reimbursements',
      '/groups/group-1/ownership-shares',
      '/groups/group-1/revenue-distribution/preview',
      '/invitations',
    ]);
  });

  it('translates the group navigation into Portuguese', async () => {
    const fixture = createFixture('group-1');
    fixture.detectChanges();
    TestBed.inject(TranslateService).use('pt');
    fixture.detectChanges();
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('.authenticated-shell__navigation')
        ?.textContent,
    ).toContain('Membros');
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Se déconnecter');
  });

  it('keeps invitations available when there is no active group', () => {
    const fixture = createFixture(null);

    fixture.detectChanges();

    const links = (fixture.nativeElement as HTMLElement).querySelectorAll(
      '.authenticated-shell__navigation a',
    );

    expect(links).toHaveLength(1);
    expect((links[0] as HTMLAnchorElement).getAttribute('href')).toBe('/invitations');
  });
});

const createFixture = (groupId: string | null): ComponentFixture<AuthenticatedShellComponent> => {
  TestBed.configureTestingModule({
    imports: [AuthenticatedShellComponent],
    providers: [
      provideLocalization(),
      { provide: QueryClient, useValue: new QueryClient() },
      provideRouter([]),
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { paramMap: convertToParamMap({ groupId }) } },
      },
      { provide: AuthSessionFacade, useValue: { signOut: vi.fn() } },
      { provide: NavigationPort, useValue: { navigateByUrl: vi.fn() } },
    ],
  });

  return TestBed.createComponent(AuthenticatedShellComponent);
};

describe('AuthenticatedShellComponent sign out', () => {
  it('clears private cached data when signing out before another member signs in', async () => {
    const queryClient = new QueryClient();
    queryClient.setQueryData(['member-groups'], [{ groupId: 'private-group' }]);
    const auth = authSessionFacadeWithToken(true);
    const navigation = new StubNavigationPort();
    TestBed.configureTestingModule({
      providers: [
        provideLocalization(),
        provideRouter([]),
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
