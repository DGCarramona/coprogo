import { Component, Input, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { provideRouter, RouterLink } from '@angular/router';

import { OwnershipShareTimelinePageComponent } from './ownership-share-timeline-page.component';
import { OwnershipShareTimelinePageViewModel } from './ownership-share-timeline-page.view-model';

@Component({ selector: 'app-authenticated-shell', template: '<ng-content />' })
class StubAuthenticatedShellComponent {
  @Input() eyebrow = '';
  @Input() title = '';
  @Input() description = '';
}

@Component({ selector: 'app-ownership-share-change-widget', template: '{{ groupId }}' })
class StubOwnershipShareChangeWidgetComponent {
  @Input({ required: true }) groupId = '';
}

describe('OwnershipShareTimelinePageComponent', () => {
  it('renders each effective ownership-share change in plain language', () => {
    const fixture = createFixture(new StubOwnershipShareTimelinePageViewModel());

    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent;
    expect(text).toContain('Répartition applicable à partir du 1 janv. 2026');
    expect(text).toContain('alice@example.com');
    expect(text).toContain('60 %');
    expect(text).toContain('bob@example.com');
    expect(text).toContain('40 %');
    expect(text).toContain('Enregistrée par alice@example.com');
  });

  it('links back to the group dashboard', () => {
    const fixture = createFixture(new StubOwnershipShareTimelinePageViewModel());

    fixture.detectChanges();

    expect(
      (fixture.nativeElement as HTMLElement).querySelector('a[href="/groups/group-1/dashboard"]'),
    ).not.toBeNull();
  });

  it('offers to record a new ownership-share change for the selected group', () => {
    const fixture = createFixture(new StubOwnershipShareTimelinePageViewModel());

    fixture.detectChanges();

    const widget = fixture.nativeElement.querySelector('app-ownership-share-change-widget');
    expect(widget?.textContent).toContain('group-1');
  });

  it('explains when no ownership-share change has been recorded', () => {
    const viewModel = new StubOwnershipShareTimelinePageViewModel();
    viewModel.changes.set([]);
    const fixture = createFixture(viewModel);

    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'Aucune répartition des revenus n’a encore été enregistrée.',
    );
  });

  it('lets the user retry after a loading failure', () => {
    const viewModel = new StubOwnershipShareTimelinePageViewModel();
    viewModel.hasLoadError.set(true);
    viewModel.isReady.set(false);
    viewModel.errorMessage.set('Historique indisponible');
    const fixture = createFixture(viewModel);
    fixture.detectChanges();

    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('button')?.click();

    expect(viewModel.retryCount).toBe(1);
  });
});

const createFixture = (
  viewModel: StubOwnershipShareTimelinePageViewModel,
): ComponentFixture<OwnershipShareTimelinePageComponent> => {
  TestBed.configureTestingModule({
    imports: [OwnershipShareTimelinePageComponent],
    providers: [provideRouter([])],
  });
  TestBed.overrideComponent(OwnershipShareTimelinePageComponent, {
    set: {
      imports: [
        StubAuthenticatedShellComponent,
        StubOwnershipShareChangeWidgetComponent,
        MatButtonModule,
        MatCardModule,
        MatProgressBarModule,
        RouterLink,
      ],
      providers: [{ provide: OwnershipShareTimelinePageViewModel, useValue: viewModel }],
    },
  });
  return TestBed.createComponent(OwnershipShareTimelinePageComponent);
};

class StubOwnershipShareTimelinePageViewModel {
  readonly groupId = 'group-1';
  readonly changes = signal([
    {
      id: 'change-1',
      effectiveDate: '1 janv. 2026',
      recordedBy: 'alice@example.com',
      recordedAt: '15 déc. 2025',
      shares: [
        { member: 'alice@example.com', percentage: '60 %' },
        { member: 'bob@example.com', percentage: '40 %' },
      ],
    },
  ]);
  readonly isLoading = signal(false);
  readonly hasLoadError = signal(false);
  readonly isReady = signal(true);
  readonly errorMessage = signal<string | null>(null);
  retryCount = 0;

  retry(): void {
    this.retryCount += 1;
  }
}
