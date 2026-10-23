import { Component, Input } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { provideRouter, RouterLink } from '@angular/router';

import { ExpenseListPageComponent } from './expense-list-page.component';
import { ExpenseListPageViewModel } from './expense-list-page.view-model';

@Component({ selector: 'app-authenticated-shell', template: '<ng-content />' })
class StubAuthenticatedShellComponent {
  @Input() eyebrow = '';
  @Input() title = '';
  @Input() description = '';
}

@Component({ selector: 'app-expense-list-widget', template: '' })
class StubExpenseListWidgetComponent {
  @Input({ required: true }) groupId = '';
}

describe('ExpenseListPageComponent', () => {
  it('shows the expenses of the route group', () => {
    const fixture = createFixture({ groupId: 'group-1' });

    fixture.detectChanges();

    const widget = fixture.debugElement.query(By.directive(StubExpenseListWidgetComponent));
    expect(widget.componentInstance.groupId).toBe('group-1');
  });

  it('links back to the group dashboard', () => {
    const fixture = createFixture({ groupId: 'group-1' });

    fixture.detectChanges();

    expect(
      (fixture.nativeElement as HTMLElement).querySelector('a[href="/groups/group-1/dashboard"]'),
    ).not.toBeNull();
  });

  it('explains when the route has no group', () => {
    const fixture = createFixture({ groupId: null });

    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'Aucun groupe n’est sélectionné.',
    );
    expect(fixture.debugElement.query(By.directive(StubExpenseListWidgetComponent))).toBeNull();
  });
});

const createFixture = (
  viewModel: Pick<ExpenseListPageViewModel, 'groupId'>,
): ComponentFixture<ExpenseListPageComponent> => {
  TestBed.configureTestingModule({
    imports: [ExpenseListPageComponent],
    providers: [provideRouter([])],
  });
  TestBed.overrideComponent(ExpenseListPageComponent, {
    set: {
      imports: [
        StubAuthenticatedShellComponent,
        StubExpenseListWidgetComponent,
        MatButtonModule,
        MatCardModule,
        RouterLink,
      ],
      providers: [{ provide: ExpenseListPageViewModel, useValue: viewModel }],
    },
  });

  return TestBed.createComponent(ExpenseListPageComponent);
};
