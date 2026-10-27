import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';

import { PendingInvitationsPageComponent } from './pending-invitations-page.component';
import { PendingInvitationsViewModel } from './pending-invitations.view-model';
import { CreateFirstGroupViewModel } from './create-first-group.view-model';
import { MemberGroupsPort } from '../../application/group/member-groups.port';
import { AuthSessionFacade } from '../../application/auth/auth-session.facade';
import { defaultAuthSessionFacade } from '../../../../__test__/app/application/auth/stub-google-id-token.port';
import { NavigationPort } from '../../application/shared/navigation.port';
import { StubNavigationPort } from '../../../../__test__/app/application/shared/stub-navigation.port';
import { PendingGroupInvitationsPort } from '../../application/group/pending-group-invitations.port';
import { GroupCreationPort } from '../../application/group/group-creation.port';

describe('PendingInvitationsPageComponent', () => {
  it('keeps joined groups accessible when there are no pending invitations', async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideTanStackQuery(new QueryClient()),
        { provide: AuthSessionFacade, useValue: defaultAuthSessionFacade },
        { provide: NavigationPort, useClass: StubNavigationPort },
        {
          provide: MemberGroupsPort,
          useValue: {
            list: async () => [
              {
                groupId: 'joined-group',
                createdBy: 'alice@example.com',
                createdAt: new Date('2026-01-01'),
              },
            ],
          },
        },
        { provide: PendingGroupInvitationsPort, useValue: { listPending: async () => [] } },
        { provide: GroupCreationPort, useValue: { create: async () => 'new-group' } },
        PendingInvitationsViewModel,
        CreateFirstGroupViewModel,
      ],
    });
    const fixture = TestBed.createComponent(PendingInvitationsPageComponent);
    fixture.autoDetectChanges();
    const host = fixture.nativeElement as HTMLElement;

    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(host.querySelector('a[href="/groups/joined-group/dashboard"]')).not.toBeNull();
    });
    expect(host.textContent).toContain('Créer un groupe');
    expect(host.textContent).not.toContain('premier groupe');
  });
});
