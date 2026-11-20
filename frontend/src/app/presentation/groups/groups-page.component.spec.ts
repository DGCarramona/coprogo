import { provideLocalization } from '../i18n/provide-localization';
import { provideRouter } from '@angular/router';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { MemberGroupsPort } from '../../application/group/member-groups.port';
import { GroupCreationPort } from '../../application/group/group-creation.port';
import { GroupRenamingPort } from '../../application/group/group-renaming.port';
import { StubGroupRenamingPort } from '../../../../__test__/app/application/group/stub-group-renaming.port';
import { AuthSessionFacade } from '../../application/auth/auth-session.facade';
import { defaultAuthSessionFacade } from '../../../../__test__/app/application/auth/stub-google-id-token.port';
import { NavigationPort } from '../../application/shared/navigation.port';
import { StubNavigationPort } from '../../../../__test__/app/application/shared/stub-navigation.port';
import { By } from '@angular/platform-browser';
import { TranslatePipe } from '@ngx-translate/core';
import { Component, EventEmitter, Input, Output, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { GroupsPageComponent } from './groups-page.component';
import { CreateFirstGroupViewModel } from './create-first-group.view-model';

@Component({ selector: 'app-authenticated-shell', template: '<ng-content />' })
class StubAuthenticatedShellComponent {
  @Input() eyebrow = '';
  @Input() title = '';
  @Input() description = '';

  readonly signOut = vi.fn();
}

@Component({ selector: 'app-create-first-group-card', template: '<p>Créer un groupe</p>' })
class StubCreateFirstGroupCardComponent {
  @Input() createdGroupId: string | null = null;
  @Input() errorMessage: string | null = null;
  @Input() creating = false;
  @Output() createRequested = new EventEmitter<string>();
}

@Component({ selector: 'app-member-groups-widget', template: '' })
class StubMemberGroupsWidgetComponent {}

describe('GroupsPageComponent', () => {
  it('forwards the chosen group name when creating a group', () => {
    const fixture = createFixture();
    fixture.detectChanges();
    fixture.debugElement
      .query(By.directive(StubCreateFirstGroupCardComponent))
      .componentInstance.createRequested.emit('Appartement');
    expect(
      fixture.debugElement.injector.get(CreateFirstGroupViewModel).createGroup,
    ).toHaveBeenCalledWith('Appartement');
  });
  it('shows groups and creation without a pending-invitation acceptance step', () => {
    const fixture = createFixture();

    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('app-member-groups-widget')).not.toBeNull();
    expect(host.querySelector('app-create-first-group-card')).not.toBeNull();
    expect(host.querySelector('app-pending-invitations-list')).toBeNull();
  });
});

const createFixture = (): ComponentFixture<GroupsPageComponent> => {
  TestBed.configureTestingModule({
    imports: [GroupsPageComponent],
    providers: [provideLocalization()],
  });
  TestBed.overrideComponent(GroupsPageComponent, {
    set: {
      imports: [
        TranslatePipe,
        StubMemberGroupsWidgetComponent,
        StubAuthenticatedShellComponent,
        StubCreateFirstGroupCardComponent,
      ],
      providers: [
        { provide: CreateFirstGroupViewModel, useValue: new StubCreateFirstGroupViewModel() },
      ],
    },
  });

  return TestBed.createComponent(GroupsPageComponent);
};

class StubCreateFirstGroupViewModel {
  readonly createdGroupId = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly isCreatingGroup = signal(false);

  readonly createGroup = vi.fn<(name: string) => Promise<void>>().mockResolvedValue(undefined);
}

describe('GroupsPageComponent', () => {
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
        { provide: GroupCreationPort, useValue: { create: async () => 'new-group' } },
        { provide: GroupRenamingPort, useClass: StubGroupRenamingPort },
        CreateFirstGroupViewModel,
      ],
    });
    const fixture = TestBed.createComponent(GroupsPageComponent);
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
