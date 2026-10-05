import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { Observable, of } from 'rxjs';

import { GroupMembersPort } from '../../../application/group/group-members.port';
import {
  DirectReimbursementRecordingPort,
  type DirectReimbursementRecordingCommand,
} from '../../../application/reimbursement/direct-reimbursement-recording.port';
import type { GroupMember } from '../../../domain/group/group-member';
import { DirectReimbursementWidgetComponent } from './direct-reimbursement-widget.component';

describe('DirectReimbursementWidgetComponent', () => {
  it('submits the payer, amount and reimbursement date entered by the user', async () => {
    const recordingPort = new StubDirectReimbursementRecordingPort();
    const { fixture, host } = createFixture(recordingPort);
    await waitFor(() => {
      fixture.detectChanges();
      return host.querySelector('option[value="bob@example.com"]') !== null;
    });

    setValue(host, fixture, 'select[aria-label="Personne qui a rembourse"]', 'bob@example.com');
    setValue(host, fixture, 'input[aria-label="Montant rembourse en euros"]', '25,50');
    setValue(host, fixture, 'input[aria-label="Date du remboursement"]', '2026-10-01T09:30');
    expect(fixture.componentInstance.viewModel.form().value()).toEqual({
      paidBy: 'bob@example.com',
      amountInEuros: '25,50',
      reimbursedAt: '2026-10-01T09:30',
    });
    const submitButton = requiredButton(host, 'button[type="submit"]');
    expect(submitButton.disabled).toBe(false);
    submitButton.click();

    await waitFor(() => recordingPort.commands.length === 1);
    expect(recordingPort.commands).toEqual([
      {
        groupId: 'group-1',
        paidBy: 'bob@example.com',
        amountInCents: 2550,
        reimbursedAt: new Date('2026-10-01T09:30'),
      },
    ]);
  });

  it('keeps submission disabled until every field is valid', async () => {
    const { fixture, host } = createFixture(new StubDirectReimbursementRecordingPort());
    await waitFor(() => {
      fixture.detectChanges();
      return host.querySelector('option[value="bob@example.com"]') !== null;
    });

    expect(requiredButton(host, 'button[type="submit"]').disabled).toBe(true);

    setValue(host, fixture, 'select[aria-label="Personne qui a rembourse"]', 'bob@example.com');
    setValue(host, fixture, 'input[aria-label="Montant rembourse en euros"]', '25,555');
    setValue(host, fixture, 'input[aria-label="Date du remboursement"]', '2026-10-01T09:30');

    expect(requiredButton(host, 'button[type="submit"]').disabled).toBe(true);
  });

  it('keeps the completed form open when recording fails', async () => {
    const recordingPort = new StubDirectReimbursementRecordingPort();
    recordingPort.failure = new Error('Service indisponible');
    const { fixture, host } = createFixture(recordingPort);
    await waitFor(() => {
      fixture.detectChanges();
      return host.querySelector('option[value="bob@example.com"]') !== null;
    });
    setValue(host, fixture, 'select[aria-label="Personne qui a rembourse"]', 'bob@example.com');
    setValue(host, fixture, 'input[aria-label="Montant rembourse en euros"]', '25,50');
    setValue(host, fixture, 'input[aria-label="Date du remboursement"]', '2026-10-01T09:30');

    requiredButton(host, 'button[type="submit"]').click();
    await waitFor(() => {
      fixture.detectChanges();
      return host.querySelector('[role="alert"]') !== null;
    });

    expect({
      formIsVisible: host.querySelector('form') !== null,
      payer: requiredField(host, 'select[aria-label="Personne qui a rembourse"]').value,
      amount: requiredField(host, 'input[aria-label="Montant rembourse en euros"]').value,
      date: requiredField(host, 'input[aria-label="Date du remboursement"]').value,
    }).toEqual({
      formIsVisible: true,
      payer: 'bob@example.com',
      amount: '25,50',
      date: '2026-10-01T09:30',
    });
  });
});

const createFixture = (
  recordingPort: StubDirectReimbursementRecordingPort,
): { fixture: ComponentFixture<DirectReimbursementWidgetComponent>; host: HTMLElement } => {
  TestBed.configureTestingModule({
    imports: [DirectReimbursementWidgetComponent],
    providers: [
      provideTanStackQuery(new QueryClient({ defaultOptions: { queries: { retry: false } } })),
      { provide: GroupMembersPort, useClass: StubGroupMembersPort },
      { provide: DirectReimbursementRecordingPort, useValue: recordingPort },
    ],
  });
  const fixture = TestBed.createComponent(DirectReimbursementWidgetComponent);
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

class StubDirectReimbursementRecordingPort extends DirectReimbursementRecordingPort {
  readonly commands: DirectReimbursementRecordingCommand[] = [];
  failure: Error | null = null;

  override async record(command: DirectReimbursementRecordingCommand): Promise<void> {
    this.commands.push(command);
    if (this.failure) throw this.failure;
  }
}

const requiredField = (
  host: HTMLElement,
  selector: string,
): HTMLInputElement | HTMLSelectElement => {
  const field = host.querySelector<HTMLInputElement | HTMLSelectElement>(selector);
  if (field === null) throw new Error(`Champ absent: ${selector}`);
  return field;
};

const setValue = (
  host: HTMLElement,
  fixture: ComponentFixture<DirectReimbursementWidgetComponent>,
  selector: string,
  value: string,
): void => {
  const field = requiredField(host, selector);
  field.value = value;
  field.dispatchEvent(new Event('input'));
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
