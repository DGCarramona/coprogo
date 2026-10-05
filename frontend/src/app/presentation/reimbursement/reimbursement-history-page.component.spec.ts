import { Component, Input, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { provideRouter, RouterLink } from '@angular/router';

import { ReimbursementHistoryPageComponent } from './reimbursement-history-page.component';
import { ReimbursementHistoryPageViewModel } from './reimbursement-history-page.view-model';
import { ReimbursementSupportingDocumentsComponent } from './reimbursement-supporting-documents.component';

@Component({ selector: 'app-authenticated-shell', template: '<ng-content />' })
class StubAuthenticatedShellComponent {
  @Input() eyebrow = '';
  @Input() title = '';
  @Input() description = '';
}

@Component({ selector: 'app-direct-reimbursement-widget', template: '' })
class StubDirectReimbursementWidgetComponent {
  @Input({ required: true }) groupId = '';
}

@Component({ selector: 'app-documented-reimbursement-widget', template: '' })
class StubDocumentedReimbursementWidgetComponent {
  @Input({ required: true }) groupId = '';
}

@Component({ selector: 'app-reimbursement-review', template: '' })
class StubReimbursementReviewComponent {
  @Input({ required: true }) groupId = '';
  @Input({ required: true }) reimbursementId = '';
}

describe('ReimbursementHistoryPageComponent', () => {
  it('renders the reimbursement history without exposing technical status labels', () => {
    const viewModel = new StubReimbursementHistoryPageViewModel();
    const fixture = createFixture(viewModel);

    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('bob@example.com');
    expect(fixture.nativeElement.textContent).toContain('alice@example.com');
    expect(fixture.nativeElement.textContent).toContain('25,00\u00a0€');
    expect(fixture.nativeElement.textContent).toContain('1 oct. 2026');
    expect(fixture.nativeElement.textContent).toContain('recu.pdf');
    expect(fixture.nativeElement.textContent).toContain('À revoir');
    expect(fixture.nativeElement.textContent).not.toContain('ACCEPTED');
  });

  it('lets the user retry after a load failure', () => {
    const viewModel = new StubReimbursementHistoryPageViewModel();
    viewModel.hasLoadError.set(true);
    viewModel.isReady.set(false);
    viewModel.errorMessage.set('Service indisponible');
    const fixture = createFixture(viewModel);
    fixture.detectChanges();

    const retryButton = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('button'),
    ).find((button) => button.textContent?.includes('Reessayer'));
    retryButton?.click();

    expect(viewModel.retryCount).toBe(1);
  });

  it('provides the current group to the direct reimbursement form', () => {
    const fixture = createFixture(new StubReimbursementHistoryPageViewModel());

    fixture.detectChanges();

    const widget = fixture.debugElement.query(By.directive(StubDirectReimbursementWidgetComponent));
    expect(widget.componentInstance.groupId).toBe('group-1');
  });

  it('provides the current group to the documented reimbursement form', () => {
    const fixture = createFixture(new StubReimbursementHistoryPageViewModel());

    fixture.detectChanges();

    const widget = fixture.debugElement.query(
      By.directive(StubDocumentedReimbursementWidgetComponent),
    );
    expect(widget.componentInstance.groupId).toBe('group-1');
  });

  it('offers review actions for a pending reimbursement', () => {
    const fixture = createFixture(new StubReimbursementHistoryPageViewModel());

    fixture.detectChanges();

    const review = fixture.debugElement.query(By.directive(StubReimbursementReviewComponent));
    expect({
      groupId: review.componentInstance.groupId,
      reimbursementId: review.componentInstance.reimbursementId,
    }).toEqual({
      groupId: 'group-1',
      reimbursementId: 'reimbursement-1',
    });
  });

  it('shows the rejection reason in plain language', () => {
    const viewModel = new StubReimbursementHistoryPageViewModel();
    viewModel.reimbursements.update((reimbursements) =>
      reimbursements.map((reimbursement) => ({
        ...reimbursement,
        awaitsReview: false,
        reviewStatusLabel: 'Refusé',
        rejectionReason: 'Le montant ne correspond pas.',
      })),
    );
    const fixture = createFixture(viewModel);

    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Refusé');
    expect(fixture.nativeElement.textContent).toContain('Le montant ne correspond pas.');
  });
});

const createFixture = (
  viewModel: StubReimbursementHistoryPageViewModel,
): ComponentFixture<ReimbursementHistoryPageComponent> => {
  TestBed.configureTestingModule({
    imports: [ReimbursementHistoryPageComponent],
    providers: [provideRouter([])],
  });
  TestBed.overrideComponent(ReimbursementHistoryPageComponent, {
    set: {
      imports: [
        StubAuthenticatedShellComponent,
        StubDirectReimbursementWidgetComponent,
        StubDocumentedReimbursementWidgetComponent,
        StubReimbursementReviewComponent,
        MatButtonModule,
        MatCardModule,
        MatProgressBarModule,
        ReimbursementSupportingDocumentsComponent,
        RouterLink,
      ],
      providers: [{ provide: ReimbursementHistoryPageViewModel, useValue: viewModel }],
    },
  });
  return TestBed.createComponent(ReimbursementHistoryPageComponent);
};

class StubReimbursementHistoryPageViewModel {
  readonly groupId = 'group-1';
  readonly reimbursements = signal([
    {
      id: 'reimbursement-1',
      paidBy: 'bob@example.com',
      receivedBy: 'alice@example.com',
      amount: '25,00\u00a0€',
      reimbursedAt: '1 oct. 2026',
      awaitsReview: true,
      reviewStatusLabel: 'À revoir',
      rejectionReason: null as string | null,
      documents: [
        {
          sourceUploadIntent: 'upload-intent-1',
          fileName: 'recu.pdf',
          uploader: 'bob@example.com',
          attachedAt: '1 oct. 2026',
          downloadUrl: 'https://documents.example.test/recu.pdf',
        },
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
