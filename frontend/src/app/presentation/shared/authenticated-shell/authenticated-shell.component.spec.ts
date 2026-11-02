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
      '/groups/group-1/expenses',
      '/groups/group-1/reimbursements',
      '/groups/group-1/ownership-shares',
      '/groups/group-1/revenue-distribution/preview',
      '/invitations',
    ]);
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
