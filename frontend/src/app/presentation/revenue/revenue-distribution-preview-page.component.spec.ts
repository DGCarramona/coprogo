import { Component, Input } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormField, FormRoot } from '@angular/forms/signals';
import { ActivatedRoute, convertToParamMap, provideRouter, RouterLink } from '@angular/router';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';

import {
  RevenueDistributionPreviewPort,
  type RevenueDistributionPreviewQuery,
} from '../../application/revenue/revenue-distribution-preview.port';
import type { RevenueDistributionPreview } from '../../domain/revenue/revenue-distribution-preview';
import { RevenueDistributionPreviewPageComponent } from './revenue-distribution-preview-page.component';

@Component({ selector: 'app-authenticated-shell', template: '<ng-content />' })
class StubAuthenticatedShellComponent {
  @Input() eyebrow = '';
  @Input() title = '';
  @Input() description = '';
}

describe('RevenueDistributionPreviewPageComponent', () => {
  it('lets a member preview and read the distribution in plain language', async () => {
    const port = new StubRevenueDistributionPreviewPort();
    const { fixture, host } = createFixture(port);
    fixture.detectChanges();

    setValue(host, fixture, 'input[aria-label="Montant à répartir en euros"]', '25,50');
    setValue(host, fixture, 'input[aria-label="Date de répartition"]', '2026-11-01');
    requiredButton(host, 'button[type="submit"]').click();

    await waitFor(() => {
      fixture.detectChanges();
      return host.textContent?.includes('alice@example.com') ?? false;
    });
    expect(port.queries).toEqual([
      {
        groupId: 'group-1',
        amountInCents: 2550,
        effectiveDate: new Date(2026, 10, 1),
      },
    ]);
    expect(host.textContent).toContain('25,50 €');
    expect(host.textContent).toContain('alice@example.com');
    expect(host.textContent).toContain('15,30 €');
    expect(host.textContent).toContain('bob@example.com');
    expect(host.textContent).toContain('10,20 €');
  });

  it('keeps preview disabled until the amount and date are valid', () => {
    const { fixture, host } = createFixture(new StubRevenueDistributionPreviewPort());
    fixture.detectChanges();

    expect(requiredButton(host, 'button[type="submit"]').disabled).toBe(true);

    setValue(host, fixture, 'input[aria-label="Montant à répartir en euros"]', '25,555');
    setValue(host, fixture, 'input[aria-label="Date de répartition"]', '2026-11-01');

    expect(requiredButton(host, 'button[type="submit"]').disabled).toBe(true);
  });

  it('links back to the selected group dashboard', () => {
    const { fixture, host } = createFixture(new StubRevenueDistributionPreviewPort());
    fixture.detectChanges();

    expect(host.querySelector('a[href="/groups/group-1/dashboard"]')).not.toBeNull();
  });

  it('explains when no group is selected', () => {
    const { fixture, host } = createFixture(new StubRevenueDistributionPreviewPort(), null);

    fixture.detectChanges();

    expect(host.querySelector('[role="alert"]')?.textContent).toContain(
      'Aucun groupe n’est sélectionné.',
    );
    expect(host.querySelector('form')).toBeNull();
  });

  it('keeps the request editable and lets the member adjust it after a preview failure', async () => {
    const port = new StubRevenueDistributionPreviewPort();
    port.failure = new Error('Aucune quote-part ne s’applique à cette date.');
    const { fixture, host } = createFixture(port);
    fixture.detectChanges();
    setValue(host, fixture, 'input[aria-label="Montant à répartir en euros"]', '25,50');
    setValue(host, fixture, 'input[aria-label="Date de répartition"]', '2026-11-01');

    requiredButton(host, 'button[type="submit"]').click();
    await waitFor(() => {
      fixture.detectChanges();
      return host.querySelector('[role="alert"]') !== null;
    });

    expect(host.textContent).toContain('Aucune quote-part ne s’applique à cette date.');
    expect(requiredInput(host, 'input[aria-label="Montant à répartir en euros"]').value).toBe(
      '25,50',
    );
    expect(requiredInput(host, 'input[aria-label="Date de répartition"]').value).toBe('2026-11-01');

    port.failure = null;
    setValue(host, fixture, 'input[aria-label="Date de répartition"]', '2026-10-01');
    requiredButton(host, 'button[type="submit"]').click();
    await waitFor(() => {
      fixture.detectChanges();
      return host.textContent?.includes('alice@example.com') ?? false;
    });

    expect(port.queries).toHaveLength(2);
  });
});

const createFixture = (
  port: StubRevenueDistributionPreviewPort,
  groupId: string | null = 'group-1',
): { fixture: ComponentFixture<RevenueDistributionPreviewPageComponent>; host: HTMLElement } => {
  TestBed.configureTestingModule({
    imports: [RevenueDistributionPreviewPageComponent],
    providers: [
      provideRouter([]),
      provideTanStackQuery(new QueryClient()),
      {
        provide: ActivatedRoute,
        useValue: {
          snapshot: { paramMap: convertToParamMap(groupId === null ? {} : { groupId }) },
        },
      },
      { provide: RevenueDistributionPreviewPort, useValue: port },
    ],
  });
  TestBed.overrideComponent(RevenueDistributionPreviewPageComponent, {
    set: {
      imports: [
        StubAuthenticatedShellComponent,
        FormField,
        FormRoot,
        MatButtonModule,
        MatCardModule,
        RouterLink,
      ],
    },
  });
  const fixture = TestBed.createComponent(RevenueDistributionPreviewPageComponent);
  return { fixture, host: fixture.nativeElement as HTMLElement };
};

class StubRevenueDistributionPreviewPort extends RevenueDistributionPreviewPort {
  readonly queries: RevenueDistributionPreviewQuery[] = [];
  failure: Error | null = null;

  override async preview(
    query: RevenueDistributionPreviewQuery,
  ): Promise<RevenueDistributionPreview> {
    this.queries.push(query);
    if (this.failure !== null) throw this.failure;
    return {
      groupId: query.groupId,
      effectiveDate: query.effectiveDate,
      totalAmountInCents: query.amountInCents,
      allocations: [
        { member: 'alice@example.com', amountInCents: 1530 },
        { member: 'bob@example.com', amountInCents: 1020 },
      ],
    };
  }
}

const setValue = (
  host: HTMLElement,
  fixture: ComponentFixture<RevenueDistributionPreviewPageComponent>,
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
