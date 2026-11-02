import { Component, EventEmitter, Input, Output, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatCardModule } from '@angular/material/card';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { PendingInvitationsPageComponent } from './pending-invitations-page.component';
import { CreateFirstGroupViewModel } from './create-first-group.view-model';
import { PendingInvitationsViewModel } from './pending-invitations.view-model';

@Component({ selector: 'app-authenticated-shell', template: '<ng-content />' })
class StubAuthenticatedShellComponent {
  @Input() eyebrow = '';
  @Input() title = '';
  @Input() description = '';

  readonly signOut = vi.fn();
}

@Component({ selector: 'app-create-first-group-card', template: '<p>Créer le premier groupe</p>' })
class StubCreateFirstGroupCardComponent {
  @Input() createdGroupId: string | null = null;
  @Input() errorMessage: string | null = null;
  @Input() creating = false;
  @Output() createRequested = new EventEmitter<void>();
}

@Component({ selector: 'app-pending-invitations-list', template: '<p>Invitation de groupe</p>' })
class StubPendingInvitationsListComponent {
  @Input() invitations: readonly unknown[] = [];
  @Input() busyInvitationId: string | null = null;
  @Input() errorMessage: string | null = null;
  @Output() invitationAccepted = new EventEmitter<string>();
}

describe('PendingInvitationsPageComponent', () => {
  it('explains the first step when there are no invitations', () => {
    const fixture = createFixture(true);

    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('section[aria-labelledby="first-group-title"]')).not.toBeNull();
    expect(host.querySelector('#first-group-title')?.textContent?.trim()).toBe(
      'Commencer avec un groupe',
    );
    expect(host.querySelector('app-create-first-group-card')).not.toBeNull();
  });

  it('presents pending invitations as a separate join action', () => {
    const fixture = createFixture(false);

    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(
      host.querySelector('section[aria-labelledby="pending-invitations-title"]'),
    ).not.toBeNull();
    expect(host.querySelector('#pending-invitations-title')?.textContent?.trim()).toBe(
      'Groupes auxquels vous êtes invité',
    );
    expect(host.querySelector('app-pending-invitations-list')).not.toBeNull();
  });
});

const createFixture = (isEmpty: boolean): ComponentFixture<PendingInvitationsPageComponent> => {
  TestBed.configureTestingModule({
    imports: [PendingInvitationsPageComponent],
  });
  TestBed.overrideComponent(PendingInvitationsPageComponent, {
    set: {
      imports: [
        StubAuthenticatedShellComponent,
        StubCreateFirstGroupCardComponent,
        StubPendingInvitationsListComponent,
        MatCardModule,
        MatProgressBarModule,
      ],
      providers: [
        {
          provide: PendingInvitationsViewModel,
          useValue: new StubPendingInvitationsViewModel(isEmpty),
        },
        { provide: CreateFirstGroupViewModel, useValue: new StubCreateFirstGroupViewModel() },
      ],
    },
  });

  return TestBed.createComponent(PendingInvitationsPageComponent);
};

class StubPendingInvitationsViewModel {
  readonly isLoading = signal(false);
  readonly hasLoadError = signal(false);
  readonly isEmpty: ReturnType<typeof signal<boolean>>;
  readonly invitations: ReturnType<typeof signal<readonly unknown[]>>;
  readonly busyInvitationId = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);

  constructor(empty: boolean) {
    this.isEmpty = signal(empty);
    this.invitations = signal(empty ? [] : [{}]);
  }

  initialize(): Promise<void> {
    return Promise.resolve();
  }

  retry(): Promise<void> {
    return Promise.resolve();
  }

  acceptInvitation(): Promise<void> {
    return Promise.resolve();
  }
}

class StubCreateFirstGroupViewModel {
  readonly createdGroupId = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly isCreatingGroup = signal(false);

  createGroup(): Promise<void> {
    return Promise.resolve();
  }
}
