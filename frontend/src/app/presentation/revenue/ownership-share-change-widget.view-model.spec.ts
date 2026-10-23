import { Injector } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { submit } from '@angular/forms/signals';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { Observable, of, throwError } from 'rxjs';

import { GroupMembersPort } from '../../application/group/group-members.port';
import {
  OwnershipShareChangePort,
  type OwnershipShareChangeCommand,
} from '../../application/revenue/ownership-share-change.port';
import type { GroupMember } from '../../domain/group/group-member';
import { OwnershipShareChangeWidgetViewModel } from './ownership-share-change-widget.view-model';

describe('OwnershipShareChangeWidgetViewModel', () => {
  let queryClient: QueryClient;
  let membersPort: StubGroupMembersPort;
  let changePort: StubOwnershipShareChangePort;
  let injector: Injector;

  beforeEach(() => {
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    TestBed.configureTestingModule({ providers: [provideTanStackQuery(queryClient)] });
    membersPort = new StubGroupMembersPort();
    changePort = new StubOwnershipShareChangePort();
    injector = TestBed.inject(Injector);
  });

  describe('initialize', () => {
    it('loads every member of the selected group', async () => {
      const viewModel = createViewModel();

      viewModel.initialize('group-1');
      await waitFor(() => viewModel.members().length === 2);

      expect(viewModel.members()).toEqual(['alice@example.com', 'bob@example.com']);
      expect(membersPort.requestedGroupIds).toEqual(['group-1']);
    });

    it('retries loading members after a failure', async () => {
      membersPort.failure = new Error('Membres indisponibles');
      const viewModel = createViewModel();
      viewModel.initialize('group-1');
      await waitFor(() => viewModel.hasLoadError());

      expect(viewModel.loadErrorMessage()).toBe('Membres indisponibles');

      membersPort.failure = null;
      viewModel.retry();
      await waitFor(() => viewModel.members().length === 2);

      expect(membersPort.requestedGroupIds).toEqual(['group-1', 'group-1']);
    });
  });

  describe('form submission', () => {
    it('records the dated shares and refreshes the timeline', async () => {
      queryClient.setQueryData(['groups', 'group-1', 'ownership-shares'], ['cached']);
      const viewModel = createViewModel();
      viewModel.initialize('group-1');
      await waitFor(() => viewModel.members().length === 2);
      fillValidForm(viewModel);

      await expect(submit(viewModel.form)).resolves.toBe(true);

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
      expect(
        queryClient.getQueryState(['groups', 'group-1', 'ownership-shares'])?.isInvalidated,
      ).toBe(true);
      await waitFor(() => viewModel.isRecorded());
      expect(viewModel.isRecorded()).toBe(true);
      expect(viewModel.form().value()).toEqual({ effectiveDate: '', percentagesByMember: {} });
    });

    it.each([
      {
        name: 'without an effective date',
        prepare: (viewModel: OwnershipShareChangeWidgetViewModel) => {
          fillValidForm(viewModel);
          viewModel.form.effectiveDate().value.set('');
        },
      },
      {
        name: 'with an impossible effective date',
        prepare: (viewModel: OwnershipShareChangeWidgetViewModel) => {
          fillValidForm(viewModel);
          viewModel.form.effectiveDate().value.set('2026-02-30');
        },
      },
      {
        name: 'with a total different from 100 percent',
        prepare: (viewModel: OwnershipShareChangeWidgetViewModel) => {
          fillValidForm(viewModel);
          viewModel.setPercentage('bob@example.com', '30');
        },
      },
      {
        name: 'with more than two decimal places',
        prepare: (viewModel: OwnershipShareChangeWidgetViewModel) => {
          fillValidForm(viewModel);
          viewModel.setPercentage('alice@example.com', '60.001');
        },
      },
    ])('rejects submission $name', async ({ prepare }) => {
      const viewModel = createViewModel();
      viewModel.initialize('group-1');
      await waitFor(() => viewModel.members().length === 2);
      prepare(viewModel);

      await expect(submit(viewModel.form)).resolves.toBe(false);

      expect(changePort.commands).toEqual([]);
    });

    it('keeps the entered values and exposes an API failure', async () => {
      changePort.failure = new Error('Seul le créateur peut modifier les quotes-parts.');
      const viewModel = createViewModel();
      viewModel.initialize('group-1');
      await waitFor(() => viewModel.members().length === 2);
      fillValidForm(viewModel);

      await expect(submit(viewModel.form)).resolves.toBe(false);
      await waitFor(() => viewModel.hasRecordingError());

      expect(viewModel.form().value()).toEqual({
        effectiveDate: '2026-11-01',
        percentagesByMember: {
          'alice@example.com': '60,25',
          'bob@example.com': '39,75',
        },
      });
      expect(viewModel.recordingErrorMessage()).toBe(
        'Seul le créateur peut modifier les quotes-parts.',
      );
    });
  });

  const createViewModel = (): OwnershipShareChangeWidgetViewModel => {
    const viewModel = new OwnershipShareChangeWidgetViewModel(
      membersPort,
      changePort,
      queryClient,
      injector,
    );
    TestBed.tick();
    return viewModel;
  };
});

const fillValidForm = (viewModel: OwnershipShareChangeWidgetViewModel): void => {
  viewModel.form.effectiveDate().value.set('2026-11-01');
  viewModel.setPercentage('alice@example.com', '60,25');
  viewModel.setPercentage('bob@example.com', '39,75');
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
    if (this.failure) throw this.failure;
  }
}

const waitFor = async (condition: () => boolean): Promise<void> => {
  for (let attempt = 0; attempt < 50; attempt += 1) {
    TestBed.tick();
    if (condition()) return;
    await new Promise((resolve) => setTimeout(resolve));
  }

  throw new Error('La condition attendue n a pas ete atteinte.');
};
