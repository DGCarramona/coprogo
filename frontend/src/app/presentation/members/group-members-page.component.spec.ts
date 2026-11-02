import { Component, Input } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { Subject, of, throwError } from 'rxjs';
import { GroupMembersPort } from '../../application/group/group-members.port';
import { GroupMemberInvitationPort } from '../../application/group/group-member-invitation.port';
import { GroupMember } from '../../domain/group/group-member';
import { AuthenticatedShellComponent } from '../shared/authenticated-shell/authenticated-shell.component';
import { provideLocalization } from '../i18n/provide-localization';
import { GroupMembersPageComponent } from './group-members-page.component';

@Component({ selector: 'app-authenticated-shell', template: '<h1>{{ title }}</h1><ng-content />' })
class StubShell {
  @Input() title = '';
  @Input() eyebrow = '';
  @Input() description = '';
}
class StubMembers extends GroupMembersPort {
  result = of<readonly GroupMember[]>([
    { member: 'alice@example.com', joinedAt: new Date('2026-01-01') },
  ]);
  override listByGroup() {
    return this.result;
  }
}
class StubInvitations extends GroupMemberInvitationPort {
  commands: string[] = [];
  failure = false;
  pending = Promise.resolve();
  override async invite(groupId: string, email: string) {
    this.commands.push(`${groupId}:${email}`);
    if (this.failure) throw new Error('errors.members.invite');
    await this.pending;
  }
}
describe('GroupMembersPageComponent', () => {
  function setup(members = new StubMembers(), invitations = new StubInvitations()) {
    TestBed.configureTestingModule({
      providers: [
        provideLocalization(),
        provideRouter([]),
        provideTanStackQuery(new QueryClient({ defaultOptions: { queries: { retry: false } } })),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ groupId: 'group-1' }) } },
        },
        { provide: GroupMembersPort, useValue: members },
        { provide: GroupMemberInvitationPort, useValue: invitations },
      ],
    });
    TestBed.overrideComponent(GroupMembersPageComponent, {
      remove: { imports: [AuthenticatedShellComponent] },
      add: { imports: [StubShell] },
    });
    const fixture = TestBed.createComponent(GroupMembersPageComponent);
    fixture.autoDetectChanges();
    const host = fixture.nativeElement as HTMLElement;
    const ready = () =>
      vi.waitFor(() => {
        fixture.detectChanges();
        expect(host.querySelector('form')).not.toBeNull();
      });
    const submit = (email: string) => {
      const input = host.querySelector<HTMLInputElement>('input[type="email"]')!;
      input.value = email;
      input.dispatchEvent(new Event('input'));
      host.querySelector('form')!.dispatchEvent(new Event('submit', { cancelable: true }));
      fixture.detectChanges();
    };
    return { fixture, host, ready, submit, invitations };
  }
  it('shows the members, their joining dates and a link back to the group', async () => {
    const { host, ready } = setup();
    await ready();
    expect(host.querySelector('h1')?.textContent).toBe('Membres du groupe');
    expect(host.querySelector('li')?.textContent).toContain('alice@example.com');
    expect(host.querySelector('li')?.textContent).toContain('2026');
    expect(host.querySelector('a[href="/groups/group-1/dashboard"]')).not.toBeNull();
  });
  it('shows loading until members arrive', async () => {
    const members = new StubMembers();
    const result = new Subject<readonly GroupMember[]>();
    members.result = result;
    const { fixture, host, ready } = setup(members);
    fixture.detectChanges();
    expect(host.textContent).toContain('Chargement des membres');
    expect(host.querySelector('form')).toBeNull();
    result.next([]);
    result.complete();
    await ready();
    expect(host.textContent).toContain('Aucun membre à afficher.');
  });
  it('offers a retry after a read failure', async () => {
    const members = new StubMembers();
    members.result = throwError(() => new Error('errors.groups.loadMembers'));
    const { fixture, host, ready } = setup(members);
    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(host.querySelector('[role="alert"]')).not.toBeNull();
    });
    expect(host.querySelector('form')).toBeNull();
    members.result = of([]);
    host.querySelector<HTMLButtonElement>('button')!.click();
    await ready();
  });
  it('validates the email and confirms an invitation awaiting acceptance', async () => {
    const { fixture, host, ready, submit, invitations } = setup();
    await ready();
    submit('invalid');
    expect(host.querySelector('[role="alert"]')?.textContent).toContain(
      'Saisissez une adresse email valide.',
    );
    expect(invitations.commands).toEqual([]);
    submit(' BOB@Example.com ');
    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(host.querySelector('[role="status"]')?.textContent).toContain('bob@example.com');
    });
    expect(host.textContent).toContain('accepter');
    expect(invitations.commands).toEqual(['group-1:bob@example.com']);
    expect(host.querySelectorAll('li')).toHaveLength(1);
    expect(host.querySelector<HTMLInputElement>('input')?.value).toBe('');
  });
  it('disables submission while pending and shows an actionable failure', async () => {
    const invitations = new StubInvitations();
    let resolve!: () => void;
    invitations.pending = new Promise((done) => {
      resolve = done;
    });
    const { fixture, host, ready, submit } = setup(new StubMembers(), invitations);
    await ready();
    submit('bob@example.com');
    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(host.querySelector<HTMLButtonElement>('button[type="submit"]')?.disabled).toBe(true);
    });
    resolve();
    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(host.querySelector<HTMLButtonElement>('button[type="submit"]')?.disabled).toBe(false);
    });
    invitations.failure = true;
    submit('charlie@example.com');
    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(host.querySelector('[role="alert"]')?.textContent).toContain(
        'L’invitation n’a pas pu être créée.',
      );
    });
  });
});
