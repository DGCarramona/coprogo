import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { Observable, of } from 'rxjs';

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
});

const createFixture = (
  changePort: StubOwnershipShareChangePort,
): { fixture: ComponentFixture<OwnershipShareChangeWidgetComponent>; host: HTMLElement } => {
  TestBed.configureTestingModule({
    imports: [OwnershipShareChangeWidgetComponent],
    providers: [
      provideTanStackQuery(new QueryClient({ defaultOptions: { queries: { retry: false } } })),
      { provide: GroupMembersPort, useClass: StubGroupMembersPort },
      { provide: OwnershipShareChangePort, useValue: changePort },
    ],
  });
  const fixture = TestBed.createComponent(OwnershipShareChangeWidgetComponent);
  fixture.componentRef.setInput('groupId', 'group-1');
  return { fixture, host: fixture.nativeElement as HTMLElement };
};

class StubGroupMembersPort extends GroupMembersPort {
  override listByGroup(): Observable<readonly GroupMember[]> {
    return of([
      { member: 'alice@example.com', joinedAt: new Date('2026-01-01T00:00:00Z') },
      { member: 'bob@example.com', joinedAt: new Date('2026-01-02T00:00:00Z') },
    ]);
  }
}

class StubOwnershipShareChangePort extends OwnershipShareChangePort {
  readonly commands: OwnershipShareChangeCommand[] = [];

  override async record(command: OwnershipShareChangeCommand): Promise<void> {
    this.commands.push(command);
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

const waitFor = async (condition: () => boolean): Promise<void> => {
  for (let attempt = 0; attempt < 50; attempt += 1) {
    TestBed.tick();
    if (condition()) return;
    await new Promise((resolve) => setTimeout(resolve));
  }

  throw new Error('La condition attendue n a pas ete atteinte.');
};
