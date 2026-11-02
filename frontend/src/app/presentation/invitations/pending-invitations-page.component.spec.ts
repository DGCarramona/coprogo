import { provideLocalization } from '../i18n/provide-localization';
import { provideRouter } from '@angular/router';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { MemberGroupsPort } from '../../application/group/member-groups.port';
import { AuthSessionFacade } from '../../application/auth/auth-session.facade';
import { defaultAuthSessionFacade } from '../../../../__test__/app/application/auth/stub-google-id-token.port';
import { NavigationPort } from '../../application/shared/navigation.port';
import { StubNavigationPort } from '../../../../__test__/app/application/shared/stub-navigation.port';
import { PendingGroupInvitationsPort } from '../../application/group/pending-group-invitations.port';
import { GroupCreationPort } from '../../application/group/group-creation.port';
import { GroupRenamingPort } from '../../application/group/group-renaming.port';
import { StubGroupRenamingPort } from '../../../../__test__/app/application/group/stub-group-renaming.port';
import { By } from '@angular/platform-browser';
import { ErrorMessagePipe } from '../i18n/error-message.pipe';
import { TranslatePipe } from '@ngx-translate/core';
import { Component, EventEmitter, Input, Output, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatCardModule } from '@angular/material/card';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { PendingInvitationsPageComponent } from './pending-invitations-page.component';
import { CreateFirstGroupViewModel } from './create-first-group.view-model';
import { PendingInvitationsViewModel } from './pending-invitations.view-model';

@Component({ selector: 'app-authenticated-shell', template: '<ng-content />' })
class StubAuthenticatedShellComponent {
  @Input() eyebrow = '';
  @Input() title = '';
  @Input() description = '';

  readonly signOut = vi.fn();
}

@Component({ selector: 'app-create-first-group-card', template: '<p>Créer le premier groupe</p>' })
class StubCreateFirstGroupCardComponent {
  @Input() createdGroupId: string | null = null;
  @Input() errorMessage: string | null = null;
  @Input() creating = false;
  @Output() createRequested = new EventEmitter<string>();
}

@Component({ selector: 'app-pending-invitations-list', template: '<p>Invitation de groupe</p>' })
class StubPendingInvitationsListComponent {
  @Input() invitations: readonly unknown[] = [];
  @Input() busyInvitationId: string | null = null;
  @Input() errorMessage: string | null = null;
  @Output() invitationAccepted = new EventEmitter<string>();
}

@Component({ selector: 'app-member-groups-widget', template: '' })
class StubMemberGroupsWidgetComponent {}

describe('PendingInvitationsPageComponent', () => {
  it('forwards the chosen group name when creating a group', () => {
    const fixture = createFixture(true);
    fixture.detectChanges();
    fixture.debugElement
      .query(By.directive(StubCreateFirstGroupCardComponent))
      .componentInstance.createRequested.emit('Appartement');
    expect(
      fixture.debugElement.injector.get(CreateFirstGroupViewModel).createGroup,
    ).toHaveBeenCalledWith('Appartement');
  });
  it('explains the first step when there are no invitations', () => {
    const fixture = createFixture(true);

    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('section[aria-labelledby="first-group-title"]')).not.toBeNull();
    expect(host.querySelector('#first-group-title')?.textContent?.trim()).toBe(
      'Commencer avec un groupe',
    );
    expect(host.querySelector('app-create-first-group-card')).not.toBeNull();
  });

  it('presents pending invitations as a separate join action', () => {
    const fixture = createFixture(false);

    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(
      host.querySelector('section[aria-labelledby="pending-invitations-title"]'),
    ).not.toBeNull();
    expect(host.querySelector('#pending-invitations-title')?.textContent?.trim()).toBe(
      'Groupes auxquels vous êtes invité',
    );
    expect(host.querySelector('app-pending-invitations-list')).not.toBeNull();
  });
});

const createFixture = (isEmpty: boolean): ComponentFixture<PendingInvitationsPageComponent> => {
  TestBed.configureTestingModule({
    imports: [PendingInvitationsPageComponent],
    providers: [provideLocalization()],
  });
  TestBed.overrideComponent(PendingInvitationsPageComponent, {
    set: {
      imports: [
        ErrorMessagePipe,
        TranslatePipe,
        StubMemberGroupsWidgetComponent,
        StubAuthenticatedShellComponent,
        StubCreateFirstGroupCardComponent,
        StubPendingInvitationsListComponent,
        MatCardModule,
        MatProgressBarModule,
      ],
      providers: [
        {
          provide: PendingInvitationsViewModel,
          useValue: new StubPendingInvitationsViewModel(isEmpty),
        },
        { provide: CreateFirstGroupViewModel, useValue: new StubCreateFirstGroupViewModel() },
      ],
    },
  });

  return TestBed.createComponent(PendingInvitationsPageComponent);
};

class StubPendingInvitationsViewModel {
  readonly isLoading = signal(false);
  readonly hasLoadError = signal(false);
  readonly isEmpty: ReturnType<typeof signal<boolean>>;
  readonly invitations: ReturnType<typeof signal<readonly unknown[]>>;
  readonly busyInvitationId = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);

  constructor(empty: boolean) {
    this.isEmpty = signal(empty);
    this.invitations = signal(empty ? [] : [{}]);
  }

  initialize(): Promise<void> {
    return Promise.resolve();
  }

  retry(): Promise<void> {
    return Promise.resolve();
  }

  acceptInvitation(): Promise<void> {
    return Promise.resolve();
  }
}

class StubCreateFirstGroupViewModel {
  readonly createdGroupId = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly isCreatingGroup = signal(false);

  readonly createGroup = vi.fn<(name: string) => Promise<void>>().mockResolvedValue(undefined);
}

describe('PendingInvitationsPageComponent', () => {
  it('keeps joined groups accessible when there are no pending invitations', async () => {
    TestBed.configureTestingModule({
      providers: [
        provideLocalization(),
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
        { provide: GroupRenamingPort, useClass: StubGroupRenamingPort },
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
