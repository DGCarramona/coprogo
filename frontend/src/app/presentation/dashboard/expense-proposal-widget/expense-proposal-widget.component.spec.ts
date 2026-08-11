import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { NEVER, Observable, of, throwError } from 'rxjs';
import {
  ExpenseProposalPort,
  ExpenseProposalCommand,
} from '../../../application/expense/expense-proposal.port';
import { GroupMembersPort } from '../../../application/group/group-members.port';
import { GroupMember } from '../../../domain/group/group-member';
import { ExpenseProposalWidgetComponent } from './expense-proposal-widget.component';

describe('ExpenseProposalWidgetComponent', () => {
  it('offers the four allocation modes with equal selected by default', async () => {
    const { fixture, host } = createFixture(new Members());

    await waitFor(() => {
      fixture.detectChanges();

      return host.querySelector('input[type="checkbox"]') instanceof HTMLInputElement;
    });

    const allocationMode = requiredSelect(host, 'select[aria-label="Mode de répartition"]');

    expect(allocationMode.value).toBe('EQUAL');
    expect([...allocationMode.options].map((option) => [option.value, option.textContent])).toEqual(
      [
        ['EQUAL', 'Parts égales'],
        ['EQUAL_WITH_CAPS', 'Parts égales avec plafonds'],
        ['CUMULATIVE_TIERS', 'Répartition par tranches'],
        ['CUSTOM', 'Montants personnalisés'],
      ],
    );
  });

  it('shows an unavailable mode without equal fields or submission', async () => {
    const { fixture, host } = createFixture(new Members());

    await waitFor(() => {
      fixture.detectChanges();

      return host.querySelector('input[type="checkbox"]') instanceof HTMLInputElement;
    });

    const allocationMode = requiredSelect(host, 'select[aria-label="Mode de répartition"]');
    allocationMode.value = 'CUSTOM';
    allocationMode.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(host.querySelector('fieldset')).toBeNull();
    expect(host.querySelector('[role="status"]')?.textContent?.trim()).toBe(
      'Les champs de ce mode de répartition ne sont pas encore disponibles.',
    );
    expect(requiredButton(host, 'button[type="submit"]').disabled).toBe(true);
  });

  it('shows an optional maximum amount only for selected capped-mode participants', async () => {
    const { fixture, host } = createFixture(new Members());

    await waitFor(() => {
      fixture.detectChanges();
      return host.querySelector('input[type="checkbox"]') instanceof HTMLInputElement;
    });

    selectAllocationMode(host, fixture, 'EQUAL_WITH_CAPS');

    expect(host.textContent).toContain(
      'Un montant maximum est facultatif. Laissez au moins un participant sans maximum.',
    );
    expect(host.querySelector('input[aria-label="Montant maximum pour a@b.c"]')).toBeNull();

    requiredInput(host, 'input[id="equal-with-caps-participant-a@b.c"]').click();
    fixture.detectChanges();

    const maximum = requiredInput(host, 'input[aria-label="Montant maximum pour a@b.c"]');
    expect(maximum.getAttribute('aria-describedby')).toBe('equal-with-caps-help');
    expect(host.querySelector('input[aria-label="Montant maximum pour b@c.d"]')).toBeNull();
  });

  it('submits a valid equal split with caps', async () => {
    const proposals = new StubExpenseProposalPort();
    const { fixture, host } = createFixture(new Members(), proposals);

    await waitFor(() => {
      fixture.detectChanges();
      return host.querySelector('input[type="checkbox"]') instanceof HTMLInputElement;
    });

    fillSharedFields(host);
    selectAllocationMode(host, fixture, 'EQUAL_WITH_CAPS');
    requiredInput(host, 'input[id="equal-with-caps-participant-a@b.c"]').click();
    requiredInput(host, 'input[id="equal-with-caps-participant-b@c.d"]').click();
    fixture.detectChanges();
    const maximum = requiredInput(host, 'input[aria-label="Montant maximum pour b@c.d"]');
    maximum.value = '25,50';
    maximum.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const submitButton = requiredButton(host, 'button[type="submit"]');
    expect(submitButton.disabled).toBe(false);
    submitButton.click();

    await waitFor(() => proposals.commands.length === 1);
    expect(proposals.commands).toEqual([
      {
        groupId: 'group-1',
        title: 'Toiture',
        totalAmountInCents: 1250,
        allocation: {
          type: 'EQUAL_WITH_CAPS',
          participants: new Set(['a@b.c', 'b@c.d']),
          capsInCentsByMember: new Map([['b@c.d', 2550]]),
        },
      },
    ]);
  });

  it('disables capped-mode submission for an invalid maximum amount', async () => {
    const { fixture, host } = createFixture(new Members());

    await waitFor(() => {
      fixture.detectChanges();
      return host.querySelector('input[type="checkbox"]') instanceof HTMLInputElement;
    });

    fillSharedFields(host);
    selectAllocationMode(host, fixture, 'EQUAL_WITH_CAPS');
    requiredInput(host, 'input[id="equal-with-caps-participant-a@b.c"]').click();
    requiredInput(host, 'input[id="equal-with-caps-participant-b@c.d"]').click();
    fixture.detectChanges();
    const maximum = requiredInput(host, 'input[aria-label="Montant maximum pour b@c.d"]');
    maximum.value = '25,555';
    maximum.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(requiredButton(host, 'button[type="submit"]').disabled).toBe(true);

    maximum.value = '25,50';
    maximum.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(requiredButton(host, 'button[type="submit"]').disabled).toBe(false);
  });

  it('submits the filled proposal', async () => {
    const proposals = new StubExpenseProposalPort();
    const { fixture, host } = createFixture(new Members(), proposals);

    await waitFor(() => {
      fixture.detectChanges();

      return host.querySelector('input[type="checkbox"]') instanceof HTMLInputElement;
    });

    const title = requiredInput(host, 'input[aria-label="Titre"]');
    const amount = requiredInput(host, 'input[aria-label="Montant en euros"]');
    expect(host.querySelector('form')).toBeInstanceOf(HTMLFormElement);
    expect(requiredForm(host).noValidate).toBe(true);
    expect(host.querySelector('label[for="expense-title"]')).toBeInstanceOf(HTMLLabelElement);
    expect(host.querySelector('label[for="expense-amount"]')).toBeInstanceOf(HTMLLabelElement);
    expect(requiredInput(host, 'input[type="checkbox"]').checked).toBe(false);
    expect(requiredButton(host, 'button[type="submit"]').disabled).toBe(true);

    title.value = 'Toiture';
    title.dispatchEvent(new Event('input'));
    amount.value = '12,50';
    amount.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const button = requiredButton(host, 'button[type="submit"]');
    expect(button.disabled).toBe(true);
    requiredInput(host, 'input[type="checkbox"]').click();
    fixture.detectChanges();
    expect(button.disabled).toBe(false);
    button.click();

    await waitFor(() => proposals.commands.length === 1);
    await waitFor(() => fixture.componentInstance.viewModel.isProposed());
    fixture.detectChanges();

    expect(proposals.commands).toEqual([
      {
        groupId: 'group-1',
        title: 'Toiture',
        totalAmountInCents: 1250,
        allocation: {
          type: 'EQUAL',
          participants: new Set(['a@b.c']),
        },
      },
    ]);
    expect(host.textContent).toContain('La dépense a été proposée.');
  });

  it('disables submission while the Signal Form is submitting', async () => {
    const proposals = new StubExpenseProposalPort();
    proposals.useDeferredResult();
    const { fixture, host } = createFixture(new Members(), proposals);

    await waitFor(() => {
      fixture.detectChanges();

      return host.querySelector('input[type="checkbox"]') instanceof HTMLInputElement;
    });

    fillProposal(host, fixture);
    const button = requiredButton(host, 'button[type="submit"]');
    button.click();

    await waitFor(() => fixture.componentInstance.viewModel.isProposing());
    fixture.detectChanges();

    expect(button.disabled).toBe(true);

    proposals.resolveDeferred();
  });

  it('shows member loading', async () => {
    const members = new Members();
    members.loading = true;
    const { fixture, host } = createFixture(members);

    await waitFor(() => {
      fixture.detectChanges();

      return host.textContent?.includes('Chargement des membres…') ?? false;
    });

    expect(host.querySelector('[role="status"]')).toBeInstanceOf(HTMLElement);
  });

  it('shows the member error and retries loading', async () => {
    const members = new Members();
    members.failure = new Error('Membres indisponibles');
    const { fixture, host } = createFixture(members);

    await waitFor(() => {
      fixture.detectChanges();

      return host.textContent?.includes('Membres indisponibles') ?? false;
    });

    const retry = requiredButton(host, 'button[aria-label="Réessayer"]');
    expect(host.querySelector('[role="alert"]')).toBeInstanceOf(HTMLElement);

    members.failure = null;
    retry.click();

    await waitFor(() => {
      fixture.detectChanges();

      return host.querySelector('input[type="checkbox"]') instanceof HTMLInputElement;
    });

    expect(members.requestedGroupIds).toEqual(['group-1', 'group-1']);
  });

  it('shows a proposal error', async () => {
    const proposals = new StubExpenseProposalPort();
    proposals.failure = new Error('Proposition indisponible');
    const { fixture, host } = createFixture(new Members(), proposals);

    await waitFor(() => {
      fixture.detectChanges();

      return host.querySelector('input[type="checkbox"]') instanceof HTMLInputElement;
    });

    fillProposal(host, fixture);
    requiredButton(host, 'button[type="submit"]').click();

    await waitFor(() => fixture.componentInstance.viewModel.hasProposalError());
    fixture.detectChanges();

    expect(host.textContent).toContain('Proposition indisponible');
    expect(host.querySelector('[role="alert"]')).toBeInstanceOf(HTMLElement);
  });
});

const createFixture = (
  members: Members,
  proposals = new StubExpenseProposalPort(),
): { fixture: ComponentFixture<ExpenseProposalWidgetComponent>; host: HTMLElement } => {
  TestBed.configureTestingModule({
    providers: [
      provideTanStackQuery(
        new QueryClient({
          defaultOptions: {
            queries: { retry: false },
          },
        }),
      ),
      { provide: GroupMembersPort, useValue: members },
      { provide: ExpenseProposalPort, useValue: proposals },
    ],
  });
  const fixture = TestBed.createComponent(ExpenseProposalWidgetComponent);
  fixture.componentRef.setInput('groupId', 'group-1');
  fixture.detectChanges();
  TestBed.tick();
  fixture.detectChanges();

  return { fixture, host: fixture.nativeElement };
};

const fillProposal = (
  host: HTMLElement,
  fixture: ComponentFixture<ExpenseProposalWidgetComponent>,
): void => {
  const title = requiredInput(host, 'input[aria-label="Titre"]');
  const amount = requiredInput(host, 'input[aria-label="Montant en euros"]');
  title.value = 'Toiture';
  title.dispatchEvent(new Event('input'));
  amount.value = '12,50';
  amount.dispatchEvent(new Event('input'));
  requiredInput(host, 'input[type="checkbox"]').click();
  fixture.detectChanges();
};

const fillSharedFields = (host: HTMLElement): void => {
  const title = requiredInput(host, 'input[aria-label="Titre"]');
  const amount = requiredInput(host, 'input[aria-label="Montant en euros"]');
  title.value = 'Toiture';
  title.dispatchEvent(new Event('input'));
  amount.value = '12,50';
  amount.dispatchEvent(new Event('input'));
};

const selectAllocationMode = (
  host: HTMLElement,
  fixture: ComponentFixture<ExpenseProposalWidgetComponent>,
  allocationMode: string,
): void => {
  const select = requiredSelect(host, 'select[aria-label="Mode de répartition"]');
  select.value = allocationMode;
  select.dispatchEvent(new Event('input'));
  fixture.detectChanges();
};

const requiredForm = (host: HTMLElement): HTMLFormElement => {
  const form = host.querySelector('form');
  if (!(form instanceof HTMLFormElement)) throw new Error('Formulaire absent.');
  return form;
};

const requiredInput = (host: HTMLElement, selector: string): HTMLInputElement => {
  const input = host.querySelector(selector);
  if (!(input instanceof HTMLInputElement)) throw new Error(`Champ absent: ${selector}`);
  return input;
};

const requiredSelect = (host: HTMLElement, selector: string): HTMLSelectElement => {
  const select = host.querySelector(selector);
  if (!(select instanceof HTMLSelectElement)) throw new Error(`Liste absente: ${selector}`);
  return select;
};

const requiredButton = (host: HTMLElement, selector: string): HTMLButtonElement => {
  const button = host.querySelector(selector);
  if (!(button instanceof HTMLButtonElement)) throw new Error(`Bouton absent: ${selector}`);
  return button;
};

const waitFor = async (condition: () => boolean): Promise<void> => {
  for (let attempt = 0; attempt < 50; attempt += 1) {
    if (condition()) return;
    await new Promise((resolve) => setTimeout(resolve));
  }

  throw new Error('La condition attendue n a pas ete atteinte.');
};

class Members extends GroupMembersPort {
  readonly members: readonly GroupMember[] = [
    { member: 'a@b.c', joinedAt: new Date() },
    { member: 'b@c.d', joinedAt: new Date() },
  ];
  readonly requestedGroupIds: string[] = [];
  failure: Error | null = null;
  loading = false;

  override listByGroup(groupId: string): Observable<readonly GroupMember[]> {
    this.requestedGroupIds.push(groupId);
    const failure = this.failure;

    if (this.loading) return NEVER;

    return failure === null ? of(this.members) : throwError(() => failure);
  }
}

class StubExpenseProposalPort extends ExpenseProposalPort {
  commands: ExpenseProposalCommand[] = [];
  failure: Error | null = null;
  private deferred: Promise<void> | null = null;
  private resolveDeferredPromise: (() => void) | null = null;

  override propose(command: ExpenseProposalCommand) {
    this.commands.push(command);

    if (this.failure !== null) return Promise.reject(this.failure);

    return this.deferred ?? Promise.resolve();
  }

  useDeferredResult(): void {
    this.deferred = new Promise((resolve) => {
      this.resolveDeferredPromise = resolve;
    });
  }

  resolveDeferred(): void {
    this.resolveDeferredPromise?.();
  }
}
