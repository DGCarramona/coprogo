import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';

import { MemberGroupsPort } from '../../application/group/member-groups.port';
import { MemberGroup } from '../../domain/group/member-group';
import { MemberGroupsWidgetComponent } from './member-groups-widget.component';
import { GroupRenamingPort } from '../../application/group/group-renaming.port';
import { StubGroupRenamingPort } from '../../../../__test__/app/application/group/stub-group-renaming.port';

describe('MemberGroupsWidgetComponent', () => {
  it('displays names and lets only the creator open the rename form', async () => {
    const port = new StubMemberGroupsPort();
    port.groups = [
      {
        groupId: 'own',
        createdBy: 'alice@example.com',
        createdAt: new Date(),
        name: 'Appartement',
        canRename: true,
      },
      {
        groupId: 'joined',
        createdBy: 'bob@example.com',
        createdAt: new Date(),
        name: 'Maison',
        canRename: false,
      },
    ];
    const renamePort = new StubGroupRenamingPort();
    const { fixture, host } = createFixture(port, renamePort);
    await waitForResult(fixture);
    expect(host.textContent).toContain('Appartement');
    expect(host.textContent).toContain('Maison');
    const renameButtons = host.querySelectorAll<HTMLButtonElement>(
      'button[aria-label^="Renommer le groupe"]',
    );
    expect(renameButtons.length).toBe(1);
    renameButtons[0].click();
    fixture.detectChanges();
    const input = host.querySelector<HTMLInputElement>('input[aria-label="Nouveau nom du groupe"]');
    if (!input) throw new Error('Formulaire de renommage absent');
    expect(input.value).toBe('Appartement');
    input.value = 'Appartement familial';
    input.dispatchEvent(new Event('input'));
    host.querySelector('form')!.dispatchEvent(new Event('submit', { cancelable: true }));
    await vi.waitFor(() =>
      expect(renamePort.commands).toEqual([{ groupId: 'own', name: 'Appartement familial' }]),
    );
  });
  it('shows every joined group with a link to its dashboard', async () => {
    const port = new StubMemberGroupsPort();
    port.groups = [
      {
        groupId: 'created',
        createdBy: 'alice@example.com',
        createdAt: new Date('2026-01-01'),
        name: null,
        canRename: true,
      },
      {
        groupId: 'joined',
        createdBy: 'bob@example.com',
        createdAt: new Date('2026-02-01'),
        name: null,
        canRename: false,
      },
    ];
    const { fixture, host } = createFixture(port);
    await waitForResult(fixture);

    expect([...host.querySelectorAll('a')].map((link) => link.getAttribute('href'))).toEqual([
      '/groups/created/dashboard',
      '/groups/joined/dashboard',
    ]);
    expect(host.textContent).toContain('alice@example.com');
    expect(host.textContent).toContain('bob@example.com');
  });

  it('explains when the member has not joined any group', async () => {
    const { fixture, host } = createFixture(new StubMemberGroupsPort());
    await waitForResult(fixture);

    expect(host.textContent).toContain('Vous n’avez pas encore rejoint de groupe.');
  });

  it('shows loading and then the groups', async () => {
    const port = new StubMemberGroupsPort();
    let resolve!: (groups: readonly MemberGroup[]) => void;
    port.pending = new Promise((done) => {
      resolve = done;
    });
    const { fixture, host } = createFixture(port);
    fixture.detectChanges();
    TestBed.tick();
    fixture.detectChanges();

    expect(host.querySelector('[role="status"]')?.textContent).toContain(
      'Chargement de vos groupes',
    );
    resolve([]);
    await waitForResult(fixture);
    expect(host.textContent).toContain('Vous n’avez pas encore rejoint de groupe.');
  });

  it('lets the member retry a failed group request', async () => {
    const port = new StubMemberGroupsPort();
    port.failure = new Error('Groupes indisponibles');
    const { fixture, host } = createFixture(port);
    await waitForResult(fixture);
    expect(host.querySelector('[role="alert"]')?.textContent).toContain('Groupes indisponibles');

    port.failure = null;
    host.querySelector<HTMLButtonElement>('button')!.click();
    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(host.textContent).toContain('Vous n’avez pas encore rejoint de groupe.');
    });

    expect(host.textContent).toContain('Vous n’avez pas encore rejoint de groupe.');
    expect(port.calls).toBe(2);
  });
});

async function waitForResult(fixture: ComponentFixture<MemberGroupsWidgetComponent>) {
  await vi.waitFor(() => {
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('[role="status"]')).toBeNull();
  });
}

function createFixture(port: StubMemberGroupsPort, renaming = new StubGroupRenamingPort()) {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      provideTanStackQuery(new QueryClient({ defaultOptions: { queries: { retry: false } } })),
      { provide: MemberGroupsPort, useValue: port },
      { provide: GroupRenamingPort, useValue: renaming },
    ],
  });
  const fixture = TestBed.createComponent(MemberGroupsWidgetComponent);
  fixture.autoDetectChanges();
  return { fixture, host: fixture.nativeElement as HTMLElement };
}

class StubMemberGroupsPort extends MemberGroupsPort {
  groups: readonly MemberGroup[] = [];
  failure: Error | null = null;
  calls = 0;
  pending: Promise<readonly MemberGroup[]> | null = null;
  override async list(): Promise<readonly MemberGroup[]> {
    this.calls++;
    if (this.failure) throw this.failure;
    return this.pending ?? this.groups;
  }
}
