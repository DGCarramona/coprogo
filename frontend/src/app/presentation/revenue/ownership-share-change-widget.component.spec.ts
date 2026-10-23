import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { Observable, of, throwError } from 'rxjs';

import { GroupMembersPort } from '../../application/group/group-members.port';
import {
  OwnershipShareChangePort,
  type OwnershipShareChangeCommand,
} from '../../application/revenue/ownership-share-change.port';
import type { GroupMember } from '../../domain/group/group-member';
import { OwnershipShareChangeWidgetComponent } from './ownership-share-change-widget.component';

describe('OwnershipShareChangeWidgetComponent', () => {
  it('submits a date and one percentage for every group member', async () => {
    const changePort = new StubOwnershipShareChangePort();
    const { fixture, host } = createFixture(changePort);
    await waitFor(() => {
      fixture.detectChanges();
      return host.querySelectorAll('input[inputmode="decimal"]').length === 2;
    });

    setValue(host, fixture, 'input[aria-label="Date d effet"]', '2026-11-01');
    setValue(host, fixture, 'input[aria-label="Quote-part de alice@example.com"]', '60,25');
    setValue(host, fixture, 'input[aria-label="Quote-part de bob@example.com"]', '39,75');
    const submitButton = requiredButton(host, 'button[type="submit"]');
    expect(submitButton.disabled).toBe(false);
    submitButton.click();

    await waitFor(() => changePort.commands.length === 1);
    expect(changePort.commands).toEqual([
      {
        groupId: 'group-1',
        effectiveDate: new Date(2026, 10, 1),
        shares: [
          { member: 'alice@example.com', percentage: 60.25 },
          { member: 'bob@example.com', percentage: 39.75 },
        ],
      },
    ]);
    await waitFor(() => {
      fixture.detectChanges();
      return host.querySelector('[role="status"]') !== null;
    });
    expect(host.textContent).toContain('Les nouvelles quotes-parts ont été enregistrées.');
  });

  it('keeps submission disabled while the shares do not total 100 percent', async () => {
    const { fixture, host } = createFixture(new StubOwnershipShareChangePort());
    await waitFor(() => {
      fixture.detectChanges();
      return host.querySelectorAll('input[inputmode="decimal"]').length === 2;
    });

    setValue(host, fixture, 'input[aria-label="Date d effet"]', '2026-11-01');
    setValue(host, fixture, 'input[aria-label="Quote-part de alice@example.com"]', '60');
    setValue(host, fixture, 'input[aria-label="Quote-part de bob@example.com"]', '30');

    expect(requiredButton(host, 'button[type="submit"]').disabled).toBe(true);
    expect(host.textContent).toContain('Le total doit être exactement égal à 100 %.');
  });

  it('shows a member-loading failure and lets the user retry it', async () => {
    const membersPort = new StubGroupMembersPort();
    membersPort.failure = new Error('Membres indisponibles');
    const { fixture, host } = createFixture(new StubOwnershipShareChangePort(), membersPort);

    await waitFor(() => {
      fixture.detectChanges();
      return host.querySelector('[role="alert"]') !== null;
    });
    expect(host.textContent).toContain('Membres indisponibles');

    membersPort.failure = null;
    requiredButton(host, 'button[aria-label="Réessayer"]').click();
    await waitFor(() => {
      fixture.detectChanges();
      return host.querySelectorAll('input[inputmode="decimal"]').length === 2;
    });

    expect(membersPort.requestedGroupIds).toEqual(['group-1', 'group-1']);
  });

  it('keeps the entered shares visible after recording fails', async () => {
    const changePort = new StubOwnershipShareChangePort();
    changePort.failure = new Error('Seul le créateur peut modifier les quotes-parts.');
    const { fixture, host } = createFixture(changePort);
    await waitFor(() => {
      fixture.detectChanges();
      return host.querySelectorAll('input[inputmode="decimal"]').length === 2;
    });

    setValue(host, fixture, 'input[aria-label="Date d effet"]', '2026-11-01');
    setValue(host, fixture, 'input[aria-label="Quote-part de alice@example.com"]', '60,25');
    setValue(host, fixture, 'input[aria-label="Quote-part de bob@example.com"]', '39,75');
    requiredButton(host, 'button[type="submit"]').click();
    await waitFor(() => {
      fixture.detectChanges();
      return host.querySelector('[role="alert"]') !== null;
    });

    expect(host.textContent).toContain('Seul le créateur peut modifier les quotes-parts.');
    expect(requiredInput(host, 'input[aria-label="Date d effet"]').value).toBe('2026-11-01');
    expect(requiredInput(host, 'input[aria-label="Quote-part de alice@example.com"]').value).toBe(
      '60,25',
    );
    expect(requiredInput(host, 'input[aria-label="Quote-part de bob@example.com"]').value).toBe(
      '39,75',
    );
  });
});

const createFixture = (
  changePort: StubOwnershipShareChangePort,
  membersPort: StubGroupMembersPort = new StubGroupMembersPort(),
): { fixture: ComponentFixture<OwnershipShareChangeWidgetComponent>; host: HTMLElement } => {
  TestBed.configureTestingModule({
    imports: [OwnershipShareChangeWidgetComponent],
    providers: [
      provideTanStackQuery(new QueryClient({ defaultOptions: { queries: { retry: false } } })),
      { provide: GroupMembersPort, useValue: membersPort },
      { provide: OwnershipShareChangePort, useValue: changePort },
    ],
  });
  const fixture = TestBed.createComponent(OwnershipShareChangeWidgetComponent);
  fixture.componentRef.setInput('groupId', 'group-1');
  return { fixture, host: fixture.nativeElement as HTMLElement };
};

class StubGroupMembersPort extends GroupMembersPort {
  failure: Error | null = null;
  readonly requestedGroupIds: string[] = [];

  override listByGroup(groupId: string): Observable<readonly GroupMember[]> {
    this.requestedGroupIds.push(groupId);
    return this.failure === null
      ? of([
          { member: 'alice@example.com', joinedAt: new Date('2026-01-01T00:00:00Z') },
          { member: 'bob@example.com', joinedAt: new Date('2026-01-02T00:00:00Z') },
        ])
      : throwError(() => this.failure);
  }
}

class StubOwnershipShareChangePort extends OwnershipShareChangePort {
  readonly commands: OwnershipShareChangeCommand[] = [];
  failure: Error | null = null;

  override async record(command: OwnershipShareChangeCommand): Promise<void> {
    this.commands.push(command);
    if (this.failure !== null) throw this.failure;
  }
}

const setValue = (
  host: HTMLElement,
  fixture: ComponentFixture<OwnershipShareChangeWidgetComponent>,
  selector: string,
  value: string,
): void => {
  const input = host.querySelector<HTMLInputElement>(selector);
  if (input === null) throw new Error(`Champ absent: ${selector}`);
  input.value = value;
  input.dispatchEvent(new Event('input'));
  fixture.detectChanges();
};

const requiredButton = (host: HTMLElement, selector: string): HTMLButtonElement => {
  const button = host.querySelector<HTMLButtonElement>(selector);
  if (button === null) throw new Error(`Bouton absent: ${selector}`);
  return button;
};

const requiredInput = (host: HTMLElement, selector: string): HTMLInputElement => {
  const input = host.querySelector<HTMLInputElement>(selector);
  if (input === null) throw new Error(`Champ absent: ${selector}`);
  return input;
};

const waitFor = async (condition: () => boolean): Promise<void> => {
  for (let attempt = 0; attempt < 50; attempt += 1) {
    TestBed.tick();
    if (condition()) return;
    await new Promise((resolve) => setTimeout(resolve));
  }

  throw new Error('La condition attendue n a pas ete atteinte.');
};
