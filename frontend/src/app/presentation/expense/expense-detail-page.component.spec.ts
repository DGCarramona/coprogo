import { Component, Input, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { provideRouter, RouterLink } from '@angular/router';

import { ExpenseDetailPageComponent } from './expense-detail-page.component';
import { ExpenseDetailPageViewModel } from './expense-detail-page.view-model';

@Component({ selector: 'app-authenticated-shell', template: '<ng-content />' })
class StubAuthenticatedShellComponent {
  @Input() eyebrow = '';
  @Input() title = '';
  @Input() description = '';
}

describe('ExpenseDetailPageComponent', () => {
  it('renders the expense and every participation status in plain language', () => {
    const fixture = createFixture(new StubExpenseDetailPageViewModel());

    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent;
    expect(text).toContain('Réfection du toit');
    expect(text).toContain('60,00 €');
    expect(text).toContain('En attente de validation');
    expect(text).toContain('alice@example.com');
    expect(text).toContain('Validée');
    expect(text).toContain('bob@example.com');
    expect(text).toContain('À valider');
    expect(text).toContain('charlie@example.com');
    expect(text).toContain('Refusée');
    expect(text).not.toContain('APPROVED');
  });

  it('links back to the group expense list', () => {
    const fixture = createFixture(new StubExpenseDetailPageViewModel());

    fixture.detectChanges();

    expect(
      (fixture.nativeElement as HTMLElement).querySelector('a[href="/groups/group-1/expenses"]'),
    ).not.toBeNull();
  });

  it('lets the user retry after a loading failure', () => {
    const viewModel = new StubExpenseDetailPageViewModel();
    viewModel.detail.set(null);
    viewModel.isReady.set(false);
    viewModel.hasLoadError.set(true);
    viewModel.errorMessage.set('Détail indisponible');
    const fixture = createFixture(viewModel);
    fixture.detectChanges();

    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('button')?.click();

    expect(viewModel.retryCount).toBe(1);
  });
});

const createFixture = (
  viewModel: StubExpenseDetailPageViewModel,
): ComponentFixture<ExpenseDetailPageComponent> => {
  TestBed.configureTestingModule({
    imports: [ExpenseDetailPageComponent],
    providers: [provideRouter([])],
  });
  TestBed.overrideComponent(ExpenseDetailPageComponent, {
    set: {
      imports: [
        StubAuthenticatedShellComponent,
        MatButtonModule,
        MatCardModule,
        MatChipsModule,
        MatProgressBarModule,
        RouterLink,
      ],
      providers: [{ provide: ExpenseDetailPageViewModel, useValue: viewModel }],
    },
  });
  return TestBed.createComponent(ExpenseDetailPageComponent);
};

class StubExpenseDetailPageViewModel {
  readonly groupId = 'group-1';
  readonly detail = signal({
    id: 'expense-1',
    title: 'Réfection du toit',
    createdBy: 'alice@example.com',
    totalAmount: '60,00 €',
    createdAt: '1 juil. 2026',
    statusLabel: 'En attente de validation',
    participations: [
      {
        member: 'alice@example.com',
        amount: '20,00 €',
        statusLabel: 'Validée',
        tone: 'approved' as const,
      },
      {
        member: 'bob@example.com',
        amount: '20,00 €',
        statusLabel: 'À valider',
        tone: 'pending' as const,
      },
      {
        member: 'charlie@example.com',
        amount: '20,00 €',
        statusLabel: 'Refusée',
        tone: 'refused' as const,
      },
    ],
  } as ReturnType<ExpenseDetailPageViewModel['detail']> | null);
  readonly isLoading = signal(false);
  readonly hasLoadError = signal(false);
  readonly isReady = signal(true);
  readonly errorMessage = signal<string | null>(null);
  retryCount = 0;

  retry(): void {
    this.retryCount += 1;
  }
}
